/**
 * Journey To Joy - Auto Runner Game
 *
 * A side-scrolling auto-runner platformer with image assets for:
 * - Player character
 * - Obstacles (red blocks)
 * - Queen (final character)
 * - Health power-ups
 * - Level-specific platforms
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class JTJ extends JFrame {
    // --- Constants defining game dimensions and behaviors ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 500;
    private static final int PLAYER_WIDTH = 40;
    private static final int PLAYER_HEIGHT = 60;
    private static final int PLATFORM_WIDTH = 100;
    private static final int PLATFORM_HEIGHT = 20;
    private static final int OBSTACLE_WIDTH = 40;
    private static final int OBSTACLE_HEIGHT = 40;
    private static final int POWER_UP_SIZE = 25;
    private static final int PLAYER_SPEED = 5;
    private static final int JUMP_STRENGTH = 16;
    private static final int GRAVITY = 1;

    // Each level gets progressively longer (harder)
    private static final int[] LEVEL_LENGTHS = {2000, 3500, 5000};

    // --- Game State Variables ---
    private JPanel gamePanel;
    private JLabel scoreLabel, levelLabel;
    private JButton restartButton;
    private Timer timer;
    private boolean isGameOver = false;
    private int score = 0;
    private int health = 100;
    private int playerX, playerY;
    private int playerVelY = 0;
    private boolean isJumping = false;
    private int jumpsRemaining = 2;
    private List<Rectangle> platforms;
    private List<Rectangle> obstacles;
    private List<PowerUp> powerUps;
    private int currentLevel = 1;
    private boolean hasShield = false;
    private int shieldTime = 0;
    private Rectangle queen = null;
    private int cameraX = 0;
    private HealthBar healthBar;

    // Image assets
    private Image[] platformImages;
    private Image obstacleImage;
    private Image queenImage;
    private Image playerImage;
    private Image healthPowerUpImage;
    private Image shieldPowerUpImage;
    private Image scorePowerUpImage;

    /**
     * Enum representing available power-up types.
     */
    private enum PowerUpType {SHIELD, HEALTH, SCORE_BOOST}

    /**
     * Represents a power-up with type and position.
     */
    private class PowerUp {
        Rectangle rect;
        PowerUpType type;
        Image image;

        PowerUp(int x, int y, PowerUpType type) {
            this.rect = new Rectangle(x, y, POWER_UP_SIZE, POWER_UP_SIZE);
            this.type = type;
            this.image = switch (type) {
                case SHIELD -> shieldPowerUpImage;
                case HEALTH -> healthPowerUpImage;
                case SCORE_BOOST -> scorePowerUpImage;
            };
        }
    }

    /**
     * UI health bar to visually display player's health.
     */
    private class HealthBar {
        private int x, y, width, height;
        private int currentHealth;

        HealthBar(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.currentHealth = health;
        }

        void draw(Graphics g) {
            g.setColor(Color.RED);
            g.fillRect(x, y, width, height);
            g.setColor(Color.GREEN);
            int healthWidth = (int)((currentHealth / 100.0) * width);
            g.fillRect(x, y, healthWidth, height);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, width, height);
        }

        void update(int health) {
            this.currentHealth = health;
        }
    }

    /**
     * Main constructor. Sets up frame and initializes game.
     */
    public JTJ() {
        setTitle("Journey To Joy - Auto Runner");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        // Load all image assets
        loadImages();
        initializeGame();
    }

    /**
     * Loads all game images.
     */
    private void loadImages() {
        // Platform images for each level
        platformImages = new Image[3];
        platformImages[0] = loadImage("platform1.png");
        platformImages[1] = loadImage("platform2.png");
        platformImages[2] = loadImage("platform3.png");

        // Character and obstacle images
        obstacleImage = loadImage("obstacle.png");
        queenImage = loadImage("queen.png");
        playerImage = loadImage("player.png");

        // Power-up images
        healthPowerUpImage = loadImage("health_powerup.png");
        shieldPowerUpImage = loadImage("shield_powerup.png");
        scorePowerUpImage = loadImage("score_powerup.png");
    }

    /**
     * Loads an image from file, returns null if not found.
     */
    private Image loadImage(String filename) {
        try {
            return new ImageIcon(filename).getImage();
        } catch (Exception e) {
            System.err.println("Error loading image: " + filename);
            return null;
        }
    }

    /**
     * Initializes UI components and game loop.
     */
    private void initializeGame() {
        platforms = new ArrayList<>();
        obstacles = new ArrayList<>();
        powerUps = new ArrayList<>();
        healthBar = new HealthBar(120, 15, 200, 20);
        setupLevel(currentLevel);

        gamePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                draw(g);
            }
        };
        gamePanel.setLayout(null);

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setBounds(10, 10, 200, 20);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 14));
        gamePanel.add(scoreLabel);

        levelLabel = new JLabel("Level: 1");
        levelLabel.setBounds(10, 40, 200, 20);
        levelLabel.setFont(new Font("Arial", Font.BOLD, 14));
        gamePanel.add(levelLabel);

        restartButton = new JButton("Restart");
        restartButton.setBounds(WIDTH/2 - 50, HEIGHT/2 + 50, 100, 30);
        restartButton.addActionListener(e -> restartGame());
        restartButton.setVisible(false);
        gamePanel.add(restartButton);

        add(gamePanel);
        gamePanel.setFocusable(true);
        gamePanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE && jumpsRemaining > 0) {
                    playerVelY = -JUMP_STRENGTH;
                    isJumping = true;
                    jumpsRemaining--;
                }
            }
        });

        timer = new Timer(20, e -> {
            if (!isGameOver && !hasReachedQueen()) {
                update();
                gamePanel.repaint();
            }
        });
        timer.start();

        playerX = 100;
        playerY = HEIGHT - PLAYER_HEIGHT - 50;
    }

    /**
     * Sets up the world with increasing elements as level increases.
     */
    private void setupLevel(int level) {
        platforms.clear();
        obstacles.clear();
        powerUps.clear();
        queen = null;

        Random rand = new Random();
        int worldWidth = LEVEL_LENGTHS[level - 1];

        // Create platforms
        int platformCount = 15 + (level * 5);
        for (int i = 0; i < platformCount; i++) {
            int x = rand.nextInt(worldWidth - PLATFORM_WIDTH);
            int y = rand.nextInt(HEIGHT - 200) + 50;
            platforms.add(new Rectangle(x, y, PLATFORM_WIDTH, PLATFORM_HEIGHT));
        }

        // Create obstacles
        int obstacleCount = 20 + (level * 10);
        for (int i = 0; i < obstacleCount; i++) {
            int x = rand.nextInt(worldWidth - OBSTACLE_WIDTH);
            int y = rand.nextInt(HEIGHT - 100);
            obstacles.add(new Rectangle(x, y, OBSTACLE_WIDTH, OBSTACLE_HEIGHT));
        }

        // Create power-ups
        int powerUpCount = 5 + (level * 2);
        for (int i = 0; i < powerUpCount; i++) {
            int x = rand.nextInt(worldWidth - POWER_UP_SIZE);
            int y = rand.nextInt(HEIGHT - 100);
            PowerUpType type = PowerUpType.values()[rand.nextInt(PowerUpType.values().length)];
            powerUps.add(new PowerUp(x, y, type));
        }

        // Add Queen only on Level 3 (final goal)
        if (level == 3) {
            queen = new Rectangle(worldWidth - 150, HEIGHT - PLAYER_HEIGHT - 100, 50, 80);
        }
    }

    /**
     * Draws all game elements and overlays.
     */
    private void draw(Graphics g) {
        // Draw background based on level
        Color[] bgColors = {
                new Color(135, 206, 235), // Level 1 - light sky blue
                new Color(100, 149, 237), // Level 2 - cornflower blue
                new Color(70, 130, 180)   // Level 3 - steel blue
        };
        g.setColor(bgColors[currentLevel - 1]);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Draw platforms with level-specific images
        for (Rectangle plat : platforms) {
            if (isVisible(plat)) {
                if (platformImages[currentLevel - 1] != null) {
                    g.drawImage(platformImages[currentLevel - 1],
                            plat.x - cameraX, plat.y,
                            plat.width, plat.height, null);
                } else {
                    g.setColor(new Color(139, 69, 19)); // Brown fallback
                    g.fillRect(plat.x - cameraX, plat.y, plat.width, plat.height);
                }
            }
        }

        // Draw obstacles with image
        for (Rectangle obs : obstacles) {
            if (isVisible(obs)) {
                if (obstacleImage != null) {
                    g.drawImage(obstacleImage,
                            obs.x - cameraX, obs.y,
                            obs.width, obs.height, null);
                } else {
                    g.setColor(Color.RED); // Fallback
                    g.fillRect(obs.x - cameraX, obs.y, obs.width, obs.height);
                }
            }
        }

        // Draw power-ups with images
        for (PowerUp powerUp : powerUps) {
            if (isVisible(powerUp.rect)) {
                if (powerUp.image != null) {
                    g.drawImage(powerUp.image,
                            powerUp.rect.x - cameraX, powerUp.rect.y,
                            powerUp.rect.width, powerUp.rect.height, null);
                } else {
                    // Fallback colors
                    Color color = switch (powerUp.type) {
                        case SHIELD -> Color.CYAN;
                        case HEALTH -> Color.GREEN;
                        case SCORE_BOOST -> Color.YELLOW;
                    };
                    g.setColor(color);
                    g.fillOval(powerUp.rect.x - cameraX, powerUp.rect.y,
                            powerUp.rect.width, powerUp.rect.height);
                }
            }
        }

        // Draw shield effect if active
        if (hasShield) {
            g.setColor(new Color(0, 255, 255, 100));
            g.fillOval(playerX - cameraX - 10, playerY - 10, PLAYER_WIDTH + 20, PLAYER_HEIGHT + 20);
        }

        // Draw player with image
        if (playerImage != null) {
            g.drawImage(playerImage,
                    playerX - cameraX, playerY,
                    PLAYER_WIDTH, PLAYER_HEIGHT, null);
        } else {
            g.setColor(Color.BLUE); // Fallback
            g.fillRect(playerX - cameraX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT);
        }

        // Draw queen with image
        if (queen != null && isVisible(queen)) {
            if (queenImage != null) {
                g.drawImage(queenImage,
                        queen.x - cameraX, queen.y,
                        queen.width, queen.height, null);
            } else {
                g.setColor(Color.PINK); // Fallback
                g.fillRect(queen.x - cameraX, queen.y, queen.width, queen.height);
            }
        }

        // Draw UI elements
        healthBar.draw(g);

        if (hasShield) {
            g.setColor(Color.BLACK);
            g.drawString("Shield: " + (shieldTime / 50), WIDTH - 100, 30);
        }

        // Draw game over/complete screen
        if (isGameOver || hasReachedQueen()) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 40));

            if (isGameOver) {
                g.drawString("Game Over", WIDTH / 2 - 100, HEIGHT / 2 - 30);
            } else {
                g.drawString("Level Complete!", WIDTH / 2 - 150, HEIGHT / 2 - 30);
            }

            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("Score: " + score, WIDTH / 2 - 80, HEIGHT / 2 + 10);
            restartButton.setVisible(true);
        }
    }

    /**
     * Handles game logic, physics, collisions, and level transitions.
     */
    private void update() {
        playerX += PLAYER_SPEED;

        if (playerX > cameraX + WIDTH / 2 && cameraX < LEVEL_LENGTHS[currentLevel - 1] - WIDTH) {
            cameraX = playerX - WIDTH / 2;
        }

        playerVelY += GRAVITY;
        playerY += playerVelY;

        if (playerY + PLAYER_HEIGHT > HEIGHT) {
            playerY = HEIGHT - PLAYER_HEIGHT;
            playerVelY = 0;
            isJumping = false;
            jumpsRemaining = 2;
        }

        for (Rectangle plat : platforms) {
            if (new Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT).intersects(plat)) {
                if (playerVelY >= 0) {
                    playerY = plat.y - PLAYER_HEIGHT;
                    playerVelY = 0;
                    isJumping = false;
                    jumpsRemaining = 2;
                }
            }
        }

        if (!hasShield) {
            for (Rectangle obs : obstacles) {
                if (new Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT).intersects(obs)) {
                    health -= 10;
                    healthBar.update(health);
                    obstacles.remove(obs);
                    if (health <= 0) {
                        isGameOver = true;
                        timer.stop();
                    }
                    break;
                }
            }
        }

        for (PowerUp powerUp : powerUps) {
            if (new Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT).intersects(powerUp.rect)) {
                applyPowerUp(powerUp.type);
                powerUps.remove(powerUp);
                break;
            }
        }

        if (hasShield && --shieldTime <= 0) hasShield = false;

        if (playerX > LEVEL_LENGTHS[currentLevel - 1]) {
            if (currentLevel < 3) {
                currentLevel++;
                levelLabel.setText("Level: " + currentLevel);
                playerX = 100;
                playerY = HEIGHT - PLAYER_HEIGHT - 50;
                cameraX = 0;
                setupLevel(currentLevel);
            } else {
                timer.stop();
            }
        }

        score += 1;
        scoreLabel.setText("Score: " + score);
    }

    /**
     * Applies the effect of a collected power-up.
     */
    private void applyPowerUp(PowerUpType type) {
        switch (type) {
            case SHIELD -> {
                hasShield = true;
                shieldTime = 500;
            }
            case HEALTH -> {
                health = Math.min(100, health + 30);
                healthBar.update(health);
            }
            case SCORE_BOOST -> score += 100;
        }
    }

    /**
     * Checks if the object is within the visible screen (camera view).
     */
    private boolean isVisible(Rectangle rect) {
        return rect.x + rect.width > cameraX && rect.x < cameraX + WIDTH;
    }

    /**
     * Checks whether the player has passed the Queen (end of level 3).
     */
    private boolean hasReachedQueen() {
        return queen != null && playerX > queen.x + queen.width;
    }

    /**
     * Restarts the game state from level 1.
     */
    private void restartGame() {
        isGameOver = false;
        score = 0;
        health = 100;
        currentLevel = 1;
        playerX = 100;
        playerY = HEIGHT - PLAYER_HEIGHT - 50;
        cameraX = 0;
        hasShield = false;
        restartButton.setVisible(false);
        levelLabel.setText("Level: 1");
        setupLevel(currentLevel);
        healthBar.update(health);
        scoreLabel.setText("Score: 0");
        timer.start();
    }

    /**
     * Main entry point to launch the game.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JTJ().setVisible(true));
    }
}