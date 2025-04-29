/**
 * Journey To Joy - Auto Runner Game
 *
 * A side-scrolling auto-runner platformer where the player must navigate obstacles,
 * collect power-ups, and progress through three levels of increasing difficulty.
 * The goal of each level is to reach the end of the level. In level 3, reaching the Queen
 * character ends the game successfully. Features include:
 * - Gravity and double jump mechanics
 * - Health bar and shield power-up
 * - Randomly placed platforms, obstacles, and power-ups
 * - Level progression and scoring
 * - Restart button on game over or completion
 */

// (Imports remain unchanged)
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
        Color color;

        PowerUp(int x, int y, PowerUpType type) {
            this.rect = new Rectangle(x, y, POWER_UP_SIZE, POWER_UP_SIZE);
            this.type = type;
            this.color = switch (type) {
                case SHIELD -> Color.CYAN;
                case HEALTH -> Color.GREEN;
                case SCORE_BOOST -> Color.YELLOW;
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
        initializeGame();
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

        int platformCount = 15 + (level * 5);
        for (int i = 0; i < platformCount; i++) {
            int x = rand.nextInt(worldWidth - PLATFORM_WIDTH);
            int y = rand.nextInt(HEIGHT - 200) + 50;
            platforms.add(new Rectangle(x, y, PLATFORM_WIDTH, PLATFORM_HEIGHT));
        }

        int obstacleCount = 20 + (level * 10);
        for (int i = 0; i < obstacleCount; i++) {
            int x = rand.nextInt(worldWidth - OBSTACLE_WIDTH);
            int y = rand.nextInt(HEIGHT - 100);
            obstacles.add(new Rectangle(x, y, OBSTACLE_WIDTH, OBSTACLE_HEIGHT));
        }

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
        Color[] bgColors = {
                new Color(135, 206, 235), // Level 1 - light sky blue
                new Color(100, 149, 237), // Level 2 - cornflower blue
                new Color(70, 130, 180)   // Level 3 - steel blue
        };
        g.setColor(bgColors[currentLevel - 1]);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.setColor(new Color(139, 69, 19));
        for (Rectangle plat : platforms) {
            if (isVisible(plat)) g.fillRect(plat.x - cameraX, plat.y, plat.width, plat.height);
        }

        g.setColor(Color.RED);
        for (Rectangle obs : obstacles) {
            if (isVisible(obs)) g.fillRect(obs.x - cameraX, obs.y, obs.width, obs.height);
        }

        for (PowerUp powerUp : powerUps) {
            if (isVisible(powerUp.rect)) {
                g.setColor(powerUp.color);
                g.fillOval(powerUp.rect.x - cameraX, powerUp.rect.y, powerUp.rect.width, powerUp.rect.height);
            }
        }

        if (hasShield) {
            g.setColor(new Color(0, 255, 255, 100));
            g.fillOval(playerX - cameraX - 10, playerY - 10, PLAYER_WIDTH + 20, PLAYER_HEIGHT + 20);
        }

        g.setColor(Color.BLUE);
        g.fillRect(playerX - cameraX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT);

        if (queen != null && isVisible(queen)) {
            g.setColor(Color.PINK);
            g.fillRect(queen.x - cameraX, queen.y, queen.width, queen.height);
        }

        healthBar.draw(g);

        if (hasShield) {
            g.setColor(Color.BLACK);
            g.drawString("Shield: " + (shieldTime / 50), WIDTH - 100, 30);
        }

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
