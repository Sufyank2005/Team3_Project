import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class JTJ extends JFrame {
    // Constants
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
    private static final int[] LEVEL_LENGTHS = {2000, 3500, 5000}; // Level lengths

    // Game variables
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
    private int jumpsRemaining = 2; // Double jump
    private List<Rectangle> platforms;
    private List<Rectangle> obstacles;
    private List<PowerUp> powerUps;
    private int currentLevel = 1;
    private boolean hasShield = false;
    private int shieldTime = 0;
    private Rectangle queen;
    private int cameraX = 0;
    private HealthBar healthBar;

    private enum PowerUpType {SHIELD, HEALTH, SCORE_BOOST}

    private class PowerUp {
        Rectangle rect;
        PowerUpType type;
        Color color;

        PowerUp(int x, int y, PowerUpType type) {
            this.rect = new Rectangle(x, y, POWER_UP_SIZE, POWER_UP_SIZE);
            this.type = type;
            this.color = type == PowerUpType.SHIELD ? Color.CYAN :
                    type == PowerUpType.HEALTH ? Color.GREEN : Color.YELLOW;
        }
    }

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

    public JTJ() {
        setTitle("Journey To Joy - Auto Runner");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        initializeGame();
    }

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

    private void setupLevel(int level) {
        platforms.clear();
        obstacles.clear();
        powerUps.clear();

        Random rand = new Random();
        int worldWidth = LEVEL_LENGTHS[level-1];

        // Platforms (more spaced in later levels)
        int platformCount = 15 + (level * 5);
        for (int i = 0; i < platformCount; i++) {
            int x = rand.nextInt(worldWidth - PLATFORM_WIDTH);
            int y = rand.nextInt(HEIGHT - 200) + 50;
            platforms.add(new Rectangle(x, y, PLATFORM_WIDTH, PLATFORM_HEIGHT));
        }

        // Obstacles (more in later levels)
        int obstacleCount = 20 + (level * 10);
        for (int i = 0; i < obstacleCount; i++) {
            int x = rand.nextInt(worldWidth - OBSTACLE_WIDTH);
            int y = rand.nextInt(HEIGHT - 100);
            obstacles.add(new Rectangle(x, y, OBSTACLE_WIDTH, OBSTACLE_HEIGHT));
        }

        // Power-ups (more valuable in later levels)
        int powerUpCount = 5 + (level * 2);
        for (int i = 0; i < powerUpCount; i++) {
            int x = rand.nextInt(worldWidth - POWER_UP_SIZE);
            int y = rand.nextInt(HEIGHT - 100);
            PowerUpType type = PowerUpType.values()[rand.nextInt(PowerUpType.values().length)];
            powerUps.add(new PowerUp(x, y, type));
        }

        // Place Queen at the end
        queen = new Rectangle(worldWidth - 150, HEIGHT - PLAYER_HEIGHT - 100, 50, 80);
    }

    private void draw(Graphics g) {
        // Background (changes per level)
        Color[] bgColors = {new Color(135, 206, 235), new Color(100, 149, 237), new Color(70, 130, 180)};
        g.setColor(bgColors[currentLevel-1]);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Draw platforms
        g.setColor(new Color(139, 69, 19));
        for (Rectangle plat : platforms) {
            if (isVisible(plat)) {
                g.fillRect(plat.x - cameraX, plat.y, plat.width, plat.height);
            }
        }

        // Draw obstacles
        g.setColor(Color.RED);
        for (Rectangle obs : obstacles) {
            if (isVisible(obs)) {
                g.fillRect(obs.x - cameraX, obs.y, obs.width, obs.height);
            }
        }

        // Draw power-ups
        for (PowerUp powerUp : powerUps) {
            if (isVisible(powerUp.rect)) {
                g.setColor(powerUp.color);
                g.fillOval(powerUp.rect.x - cameraX, powerUp.rect.y, powerUp.rect.width, powerUp.rect.height);
            }
        }

        // Draw player with shield if active
        if (hasShield) {
            g.setColor(new Color(0, 255, 255, 100));
            g.fillOval(playerX - cameraX - 10, playerY - 10, PLAYER_WIDTH + 20, PLAYER_HEIGHT + 20);
        }
        g.setColor(Color.BLUE);
        g.fillRect(playerX - cameraX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT);

        // Draw Queen
        if (queen != null && isVisible(queen)) {
            g.setColor(Color.PINK);
            g.fillRect(queen.x - cameraX, queen.y, queen.width, queen.height);
        }

        // Draw UI
        healthBar.draw(g);
        if (hasShield) {
            g.setColor(Color.BLACK);
            g.drawString("Shield: " + (shieldTime/50), WIDTH - 100, 30);
        }

        // Game over/win screens
        if (isGameOver || hasReachedQueen()) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 40));

            if (isGameOver) {
                g.drawString("Game Over", WIDTH/2 - 100, HEIGHT/2 - 30);
            } else {
                g.drawString("Level Complete!", WIDTH/2 - 150, HEIGHT/2 - 30);
            }

            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("Score: " + score, WIDTH/2 - 80, HEIGHT/2 + 10);
            restartButton.setVisible(true);
        }
    }

    private void update() {
        // Automatic movement
        playerX += PLAYER_SPEED;

        // Camera follow
        if (playerX > cameraX + WIDTH/2 && cameraX < LEVEL_LENGTHS[currentLevel-1] - WIDTH) {
            cameraX = playerX - WIDTH/2;
        }

        // Apply gravity
        playerVelY += GRAVITY;
        playerY += playerVelY;

        // Floor collision
        if (playerY + PLAYER_HEIGHT > HEIGHT) {
            playerY = HEIGHT - PLAYER_HEIGHT;
            playerVelY = 0;
            isJumping = false;
            jumpsRemaining = 2; // Reset double jump on ground
        }

        // Platform collision
        for (Rectangle plat : platforms) {
            if (new Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT).intersects(plat)) {
                if (playerVelY >= 0) { // Landing on platform
                    playerY = plat.y - PLAYER_HEIGHT;
                    playerVelY = 0;
                    isJumping = false;
                    jumpsRemaining = 2; // Reset double jump
                }
            }
        }

        // Obstacle collision
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

        // Power-up collision
        for (PowerUp powerUp : powerUps) {
            if (new Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT).intersects(powerUp.rect)) {
                applyPowerUp(powerUp.type);
                powerUps.remove(powerUp);
                break;
            }
        }

        // Shield timer
        if (hasShield && --shieldTime <= 0) {
            hasShield = false;
        }

        // Level completion
        if (playerX > LEVEL_LENGTHS[currentLevel-1]) {
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

        // Score
        score += 1;
        scoreLabel.setText("Score: " + score);
    }

    private void applyPowerUp(PowerUpType type) {
        switch (type) {
            case SHIELD:
                hasShield = true;
                shieldTime = 500;
                break;
            case HEALTH:
                health = Math.min(100, health + 30);
                healthBar.update(health);
                break;
            case SCORE_BOOST:
                score += 100;
                break;
        }
    }

    private boolean isVisible(Rectangle rect) {
        return rect.x + rect.width > cameraX && rect.x < cameraX + WIDTH;
    }

    private boolean hasReachedQueen() {
        return queen != null && playerX > queen.x + queen.width;
    }

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JTJ().setVisible(true));
    }
}