/**
 * Journey To Joy - Auto Runner Game with Sprite Animations
 *
 * A side-scrolling auto-runner platformer featuring:
 * - Animated player character with running and jumping sprites
 * - Multiple levels with increasing difficulty
 * - Power-ups (health, shield, score boost)
 * - Animated obstacles and platforms
 * - Queen character as final goal
 * - Persistent top score (start screen only)
 *
 * @authors Sufyan K. Haroon K. Hana M. Fatima M. Ishaben M. Tom D
 */
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.sound.sampled.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JTJ extends JFrame {
    // --- Constants defining game dimensions and behaviors ---
    private static final int WIDTH = 700;
    private static final int HEIGHT = 500;
    private static final int PLAYER_WIDTH = 40;
    private static final int PLAYER_HEIGHT = 100;
    private static final int PLATFORM_WIDTH = 100;
    private static final int PLATFORM_HEIGHT = 20;
    private static final int OBSTACLE_WIDTH = 40;
    private static final int OBSTACLE_HEIGHT = 30;
    private static final int POWER_UP_SIZE = 25;
    private static final int PLAYER_SPEED = 5;
    private static final int JUMP_STRENGTH = 16;
    private static final int GRAVITY = 1;

    // Each level gets progressively longer (harder)
    private static final int[] LEVEL_LENGTHS = {2000, 3500, 5000};

    // Leaderboard constants - now using single top score
    private static final String HIGH_SCORES_FILE = System.getProperty("user.home") + "/JourneyToJoy_topscore.dat";

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
    private Clip backgroundMusic;
    private FloatControl volumeControl;
    private Clip victoryClip;
    private boolean victorySoundPlayed = false;

    // Top score variable
    private HighScoreEntry topScore;
    private boolean newTopScore = false;

    // Animation variables
    private BufferedImage[] runSprites;
    private BufferedImage jumpSprite;
    private int currentFrame = 0;
    private int frameDelay = 15;
    private int frameCounter = 0;
    private int lastPlayerX = 0;
    private boolean wasJumping = false;

    // Obstacle animation variables
    private BufferedImage[] obstacleSprites;
    private int currentObstacleFrame = 0;
    private int obstacleFrameDelay = 10;
    private int obstacleFrameCounter = 0;

    // Image assets
    private Image[] platformImages;
    private Image obstacleImage;
    private Image queenImage;
    private Image healthPowerUpImage;
    private Image shieldPowerUpImage;
    private Image scorePowerUpImage;
    private Image[] backgroundImages;

    /**
     * Represents a top score entry with player initials
     */
    private class HighScoreEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        String initials;
        int score;

        /**
         * Creates a new top score entry
         * @param initials Player initials (2 letters)
         * @param score The score achieved
         */
        HighScoreEntry(String initials, int score) {
            this.initials = initials;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("%2s %6d", initials, score);
        }
    }

    /**
     * Enum representing available power-up types.
     */
    private enum PowerUpType { SHIELD, HEALTH, SCORE_BOOST }

    /**
     * Enum representing game states.
     */
    private enum GameState { START, PLAYING, GAME_OVER }
    private GameState gameState = GameState.START;

    /**
     * Enum representing player animation states.
     */
    private enum AnimationState { IDLE, RUNNING, JUMPING }
    private AnimationState currentAnimation = AnimationState.IDLE;

    /**
     * Represents a power-up with type and position.
     */
    private class PowerUp {
        Rectangle rect;
        PowerUpType type;
        Image image;

        /**
         * Creates a new power-up at specified coordinates.
         *
         * @param x The x-coordinate of the power-up
         * @param y The y-coordinate of the power-up
         * @param type The type of power-up
         */
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

        /**
         * Creates a new health bar.
         *
         * @param x The x-coordinate of the health bar
         * @param y The y-coordinate of the health bar
         * @param width The width of the health bar
         * @param height The height of the health bar
         */
        HealthBar(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.currentHealth = health;
        }

        /**
         * Draws the health bar on screen.
         *
         * @param g The Graphics object to draw with
         */
        void draw(Graphics g) {
            g.setColor(Color.RED);
            g.fillRect(x, y, width, height);
            g.setColor(Color.GREEN);
            int healthWidth = (int)((currentHealth / 100.0) * width);
            g.fillRect(x, y, healthWidth, height);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, width, height);
        }

        /**
         * Updates the health bar with current health value.
         *
         * @param health The current health value
         */
        void update(int health) {
            this.currentHealth = health;
        }
    }

    /**
     * Main constructor. Sets up frame and initializes game.
     */
    public JTJ() {
        setTitle("Journey To Joy");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        // Add window listener to clean up audio
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (backgroundMusic != null) {
                    backgroundMusic.close();
                }
                if (victoryClip != null) {
                    victoryClip.close();
                }
                saveTopScore();
            }
        });

        // Add shutdown hook to ensure scores are saved
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveTopScore();
        }));

        // Load all image assets
        loadImages();
        initializeGame();
    }

    /**
     * Loads all game images from files.
     */
    private void loadImages() {
        // Platform images for each level
        platformImages = new Image[3];
        platformImages[0] = loadImage("platform1.png");
        platformImages[1] = loadImage("platform2.png");
        platformImages[2] = loadImage("platform3.png");

        backgroundImages = new Image[3];
        backgroundImages[0] = loadImage("orig1.png"); // Level 1
        backgroundImages[1] = loadImage("orig2.png"); // Level 2
        backgroundImages[2] = loadImage("orig.png");  // Level 3

        // Character and obstacle images
        obstacleImage = loadImage("obstacle.png");
        queenImage = loadImage("queen.png");

        // Power-up images
        healthPowerUpImage = loadImage("health_powerup.png");
        shieldPowerUpImage = loadImage("shield_powerup.png");
        scorePowerUpImage = loadImage("score_powerup.png");

        // Load obstacle sprite sheet
        try {
            BufferedImage obstacleSheet = ImageIO.read(new File("obstacle_sheet.png"));
            int obstacleFrameCount = 3; // Assuming 3 frames in the sheet
            obstacleSprites = new BufferedImage[obstacleFrameCount];

            // Assuming frames are laid out horizontally
            int frameWidth = obstacleSheet.getWidth() / obstacleFrameCount;
            for (int i = 0; i < obstacleFrameCount; i++) {
                obstacleSprites[i] = obstacleSheet.getSubimage(
                        i * frameWidth,
                        0,
                        frameWidth,
                        obstacleSheet.getHeight()
                );
            }
        } catch (IOException e) {
            System.err.println("Failed to load obstacle sprites: " + e.getMessage());
            // Fall back to single image
            obstacleImage = loadImage("obstacle.png");
        }
    }

    /**
     * Loads an image from file.
     *
     * @param filename The name of the image file to load
     * @return The loaded Image, or null if loading failed
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

        // Load top score at game start
        loadTopScore();

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
        restartButton.setBounds(WIDTH/2 - 50, HEIGHT - 100, 100, 30);
        restartButton.addActionListener(e -> restartGame());
        restartButton.setVisible(false);
        gamePanel.add(restartButton);

        add(gamePanel);
        gamePanel.setFocusable(true);
        gamePanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (gameState == GameState.START && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    gameState = GameState.PLAYING;
                } else if (gameState == GameState.PLAYING) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE && jumpsRemaining > 0) {
                        playerVelY = -JUMP_STRENGTH;
                        isJumping = true;
                        jumpsRemaining--;
                        playSound("jump.wav");
                    }
                }
            }
        });

        timer = new Timer(20, e -> {
            if (!isGameOver && !hasReachedQueen() && gameState == GameState.PLAYING) {
                update();
            }
            gamePanel.repaint();
        });

        timer.start();

        playerX = 100;
        playerY = HEIGHT - PLAYER_HEIGHT - 80;
        lastPlayerX = playerX;

        // Start background music
        playBackgroundMusic("background_music.wav");
    }

    /**
     * Plays background music in a loop.
     * @param filename The name of the music file to play
     */
    private void playBackgroundMusic(String filename) {
        try {
            File musicFile = new File(filename);
            AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicFile);
            backgroundMusic = AudioSystem.getClip();
            backgroundMusic.open(audioInput);

            // Get volume control and set to 50% volume
            if (backgroundMusic.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                volumeControl = (FloatControl) backgroundMusic.getControl(FloatControl.Type.MASTER_GAIN);
                float range = volumeControl.getMaximum() - volumeControl.getMinimum();
                float gain = (range * 0.65f) + volumeControl.getMinimum();
                volumeControl.setValue(gain);
            }

            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception e) {
            System.err.println("Error with background music: " + e.getMessage());
        }
    }

    /**
     * Plays the victory sound when reaching the queen
     */
    private void playVictorySound() {
        try {
            File soundFile = new File("meet_the_queen.wav");
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
            victoryClip = AudioSystem.getClip();
            victoryClip.open(audioIn);

            if (victoryClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) victoryClip.getControl(FloatControl.Type.MASTER_GAIN);
                float range = gainControl.getMaximum() - gainControl.getMinimum();
                float gain = (range * 0.7f) + gainControl.getMinimum();
                gainControl.setValue(gain);
            }

            victoryClip.start();
        } catch (Exception e) {
            System.err.println("Error playing victory sound: " + e.getMessage());
        }
    }

    /**
     * Loads the top score from file
     */
    private void loadTopScore() {
        File file = new File(HIGH_SCORES_FILE);
        if (!file.exists()) {
            topScore = new HighScoreEntry("aa", 0); // Default empty score
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof HighScoreEntry) {
                topScore = (HighScoreEntry) obj;
            } else {
                topScore = new HighScoreEntry("aa", 0);
            }
        } catch (Exception e) {
            System.err.println("Error loading top score, creating new: " + e.getMessage());
            topScore = new HighScoreEntry("aa", 0);
        }
    }

    /**
     * Saves the top score to file
     */
    private void saveTopScore() {
        try {
            File file = new File(HIGH_SCORES_FILE);
            file.getParentFile().mkdirs();

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                oos.writeObject(topScore);
            }
        } catch (IOException e) {
            System.err.println("Error saving top score: " + e.getMessage());
        }
    }

    /**
     * Prompts for initials when achieving a new top score
     * @return The user's initials (2 lowercase letters)
     */
    private String getInitials() {
        String initials = JOptionPane.showInputDialog(this,
                "New Top Score! Enter your initials (2 letters):",
                "Top Score",
                JOptionPane.PLAIN_MESSAGE);

        // Validate input
        if (initials == null) return "aa";
        initials = initials.toLowerCase().trim();
        if (initials.length() != 2 || !initials.matches("[a-z]{2}")) {
            return "aa";
        }
        return initials;
    }

    /**
     * Checks if current score beats the top score and updates if needed
     */
    private void checkAndUpdateTopScore() {
        if (score > topScore.score) {
            newTopScore = true;
            String initials = getInitials();
            topScore = new HighScoreEntry(initials, score);
            saveTopScore();
        }
    }

    /**
     * Sets up the game world for a specific level.
     *
     * @param level The level number to set up (1-3)
     */
    private void setupLevel(int level) {
        platforms.clear();
        obstacles.clear();
        powerUps.clear();
        queen = null;

        // Load sprite sheets
        try {
            // Load running animation (4 frames)
            BufferedImage runSheet = ImageIO.read(new File("mainchar_run.png"));
            int runFrameCount = 2;
            runSprites = new BufferedImage[runFrameCount];
            for (int i = 0; i < runFrameCount; i++) {
                runSprites[i] = new BufferedImage(PLAYER_WIDTH, PLAYER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = runSprites[i].createGraphics();
                g.drawImage(runSheet.getSubimage(
                                i * (runSheet.getWidth()/runFrameCount),
                                0,
                                runSheet.getWidth()/runFrameCount,
                                runSheet.getHeight()),
                        0, 0, PLAYER_WIDTH, PLAYER_HEIGHT, null);
                g.dispose();
            }

            // Load jumping sprite
            jumpSprite = ImageIO.read(new File("mainchar_jump.png"));

        } catch (IOException e) {
            System.err.println("Failed to load sprite sheets: " + e.getMessage());
            runSprites = null;
            jumpSprite = null;
        }

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
        int powerUpCount = 10 + (level * 4);
        for (int i = 0; i < powerUpCount; i++) {
            int x = rand.nextInt(worldWidth - POWER_UP_SIZE);
            int y = rand.nextInt(HEIGHT - 100);

            PowerUpType type;
            double randVal = rand.nextDouble();
            if (randVal < 0.5) {
                type = PowerUpType.SCORE_BOOST;
            } else if (randVal < 0.75) {
                type = PowerUpType.SHIELD;
            } else {
                type = PowerUpType.HEALTH;
            }

            powerUps.add(new PowerUp(x, y, type));
        }

        // Add Queen only on Level 3 (final goal)
        if (level == 3) {
            queen = new Rectangle(worldWidth - 150, HEIGHT - PLAYER_HEIGHT - 100, 80, 110);
        }
    }

    /**
     * Draws all game elements and overlays.
     *
     * @param g The Graphics object to draw with
     */
    private void draw(Graphics g) {
        if (gameState == GameState.START) {
            // Draw start screen
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Consolas", Font.BOLD, 36));
            g.drawString("Journey To Joy", WIDTH / 2 - 150, HEIGHT / 2 - 50);
            g.setFont(new Font("Consolas", Font.PLAIN, 20));
            g.drawString("Press ENTER to Start", WIDTH / 2 - 110, HEIGHT / 2 + 20);

            // Draw top score on start screen
            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("Top Score:", WIDTH / 2 - 80, HEIGHT / 2 + 60);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.drawString(topScore.toString(),
                    WIDTH / 2 - 80,
                    HEIGHT / 2 + 85);

            return;
        }

        // Draw background based on level
        if (backgroundImages[currentLevel - 1] != null) {
            g.drawImage(backgroundImages[currentLevel - 1], 0, 0, WIDTH, HEIGHT, null);
        } else {
            Color[] bgColors = {
                    new Color(135, 206, 235),
                    new Color(100, 149, 237),
                    new Color(70, 130, 180)
            };
            g.setColor(bgColors[currentLevel - 1]);
            g.fillRect(0, 0, WIDTH, HEIGHT);
        }

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

        // Draw obstacles with animation
        for (Rectangle obs : obstacles) {
            if (isVisible(obs)) {
                if (obstacleSprites != null && currentObstacleFrame < obstacleSprites.length) {
                    g.drawImage(obstacleSprites[currentObstacleFrame],
                            obs.x - cameraX, obs.y,
                            obs.width, obs.height, null);
                } else if (obstacleImage != null) {
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

        // Draw player with appropriate animation
        if (currentAnimation == AnimationState.JUMPING && jumpSprite != null) {
            g.drawImage(jumpSprite,
                    playerX - cameraX,
                    playerY,
                    PLAYER_WIDTH,
                    PLAYER_HEIGHT,
                    null);
        } else if (runSprites != null && currentFrame < runSprites.length) {
            // Scale the running sprite to match PLAYER_WIDTH and PLAYER_HEIGHT
            Image runImage = runSprites[currentFrame].getScaledInstance(PLAYER_WIDTH, PLAYER_HEIGHT, Image.SCALE_DEFAULT);
            g.drawImage(runImage,
                    playerX - cameraX,
                    playerY,
                    PLAYER_WIDTH,
                    PLAYER_HEIGHT,
                    null);
        }
        else {
            // Fallback
            g.setColor(Color.BLUE);
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
                g.drawString("You Reached Joy!", WIDTH / 2 - 150, HEIGHT / 2 - 30);
            }

            g.setFont(new Font("Arial", Font.BOLD, 24));
            g.drawString("Score: " + score, WIDTH / 2 - 80, HEIGHT / 2 + 10);

            // Highlight new top score
            if (newTopScore) {
                g.setColor(Color.YELLOW);
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("New Top Score!", WIDTH / 2 - 80, HEIGHT / 2 + 40);
            }

            restartButton.setVisible(true);
        }
    }

    /**
     * Updates game state, including player movement, collisions, and animations.
     */
    private void update() {
        playerX += PLAYER_SPEED;

        // Camera follow
        if (playerX > cameraX + WIDTH / 2 && cameraX < LEVEL_LENGTHS[currentLevel - 1] - WIDTH) {
            cameraX = playerX - WIDTH / 2;
        }

        // Apply gravity
        playerVelY += GRAVITY;
        playerY += playerVelY;

        // Ground collision
        if (playerY + PLAYER_HEIGHT > HEIGHT) {
            playerY = HEIGHT - PLAYER_HEIGHT;
            playerVelY = 0;
            isJumping = false;
            jumpsRemaining = 2;
        }

        // Platform collisions
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

        // Obstacle collisions
        if (!hasShield) {
            for (Rectangle obs : obstacles) {
                if (new Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT).intersects(obs)) {
                    health -= 10;
                    healthBar.update(health);
                    obstacles.remove(obs);
                    playSound("obstacle_impact.wav");
                    if (health <= 0) {
                        isGameOver = true;
                        timer.stop();
                        playSound("gameover (2).wav");
                    }
                    break;
                }
            }
        }

        // Power-up collisions
        for (PowerUp powerUp : powerUps) {
            if (new Rectangle(playerX, playerY, PLAYER_WIDTH, PLAYER_HEIGHT).intersects(powerUp.rect)) {
                applyPowerUp(powerUp.type);
                powerUps.remove(powerUp);
                break;
            }
        }

        // Shield timer
        if (hasShield && --shieldTime <= 0) hasShield = false;

        // Level progression
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

        // Update obstacle animation
        obstacleFrameCounter++;
        if (obstacleFrameCounter >= obstacleFrameDelay) {
            currentObstacleFrame = (currentObstacleFrame + 1) % (obstacleSprites != null ? obstacleSprites.length : 1);
            obstacleFrameCounter = 0;
        }

        // Animation control
        if (isJumping || playerVelY != 0) {
            currentAnimation = AnimationState.JUMPING;
            wasJumping = true;
        } else if (playerX != lastPlayerX) {
            currentAnimation = AnimationState.RUNNING;
            frameCounter++;
            if (frameCounter >= frameDelay) {
                currentFrame = (currentFrame + 1) % runSprites.length;
                frameCounter = 0;
            }
        } else {
            currentAnimation = AnimationState.IDLE;
            currentFrame = 0;
        }

        // Check for landing
        if (wasJumping && !isJumping && playerVelY == 0) {
            wasJumping = false;
        }

        lastPlayerX = playerX;
        scoreLabel.setText("Score: " + score);
    }

    /**
     * Applies the effect of a collected power-up.
     *
     * @param type The type of power-up to apply
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
            case SCORE_BOOST -> {
                score += 100;
                scoreLabel.setText("Score: " + score);
            }
        }
    }

    /**
     * Checks if an object is within the visible screen area.
     *
     * @param rect The rectangle to check
     * @return true if the object is visible, false otherwise
     */
    private boolean isVisible(Rectangle rect) {
        return rect.x + rect.width > cameraX && rect.x < cameraX + WIDTH;
    }

    /**
     * Checks whether the player has reached the Queen (end of level 3).
     *
     * @return true if the player has reached the Queen, false otherwise
     */
    private boolean hasReachedQueen() {
        if (queen != null && playerX > queen.x + queen.width) {
            if (!victorySoundPlayed) {
                playVictorySound();
                victorySoundPlayed = true;
            }
            return true;
        }
        return false;
    }

    /**
     * Plays a sound effect from file.
     *
     * @param filename The name of the sound file to play
     */
    private void playSound(String filename) {
        try {
            File soundFile = new File(filename);
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
        } catch (Exception e) {
            System.err.println("Error playing sound: " + e.getMessage());
        }
    }

    /**
     * Restarts the game state from level 1.
     */
    private void restartGame() {
        // Check and update top score when game ends
        if (hasReachedQueen() || isGameOver) {
            checkAndUpdateTopScore();
        }

        // Reset game state
        victorySoundPlayed = false;
        newTopScore = false;

        // Stop current music if playing
        if (backgroundMusic != null && backgroundMusic.isRunning()) {
            backgroundMusic.stop();
        }

        // Stop any playing victory sound
        if (victoryClip != null && victoryClip.isRunning()) {
            victoryClip.stop();
        }

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
        gameState = GameState.START;
        timer.start();

        // Restart music
        playBackgroundMusic("background_music.wav");
    }

    /**
     * Main entry point to launch the game.
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JTJ gameWindow = new JTJ();  // Create the game window
            gameWindow.setVisible(true);  // Make the window visible
            gameWindow.setLocationRelativeTo(null);  // Center the window on the screen
        });
    }
}