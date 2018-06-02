package main;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

public class SpriteFixer {

    /**
     * Regex used to match image filenames.
     */
    private static final String IMAGE_FILENAME_REGEX = 
            ".+\\.(?i)(bmp|jpg|gif|png)";

    /**
     * Number of pixels to sample when comparing images for similarity.
     */
    private static final int SAMPLE_COUNT = 50;

    /**
     * Number of sampled pixels that must match for images to be considered the
     * same.
     */
    private static final int MIN_MATCHED_SAMPLES = 45;

    /**
     * Colour of pixels to try and fix.
     */
    private static final int HIGHLIGHT_COLOUR = 0xffff00ff;

    /**
     * Background colour to use for the produced sprites.
     */
    private static final int BG_COLOUR = 0xff80c0ff;

    /**
     * Map of group ID -> images in that group.
     */
    private Map<Integer, List<BufferedImage>> imageGroups = new HashMap<>();
    
    /**
     * Next available group ID.
     */
    private int nextGroupId = 0;
    
    /**
     * Adds an image to the appropriate group.
     * 
     * @param image
     */
    private void add(BufferedImage image) {
        // Recognise similar images and group them
        int groupId = getGroupId(image);
        addToGroup(groupId, image);
    }

    /**
     * Determines the group to which an image should belong.
     * 
     * @param image
     * @return
     */
    private int getGroupId(BufferedImage image) {
        
        // If an image is sufficiently similar to an existing group, place it in
        // that group
        for (Integer groupId : imageGroups.keySet()) {
            BufferedImage firstImageInGroup = imageGroups.get(groupId).get(0);
            if (areImagesSimilar(image, firstImageInGroup)) {
                return groupId;
            }
        }
        
        // Otherwise, create a new group
        int returnId = nextGroupId;
        nextGroupId++;
        
        return returnId;
    }

    /**
     * Determines if 2 images are sufficiently similar to be part of the same
     * group.
     * 
     * @param image1
     * @param image2
     * @return
     */
    private boolean areImagesSimilar(BufferedImage image1, BufferedImage image2) {

        // Image dimensions must match
        if (image1.getWidth() != image2.getWidth() ||
                image1.getHeight() != image2.getHeight()) {
            return false;
        }

        // Check some random pixels to see if they match
        int matchedSamples = 0;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            int x = (int) (Math.random() * image1.getWidth());
            int y = (int) (Math.random() * image1.getHeight());
            if (image1.getRGB(x, y) == image2.getRGB(x, y)) {
                matchedSamples++;
            }
        }
        
        return matchedSamples >= MIN_MATCHED_SAMPLES;
    }

    /**
     * Adds an image to a group.
     * 
     * @param groupId
     * @param image
     */
    private void addToGroup(int groupId, BufferedImage image) {
        List<BufferedImage> imagesInGroup = imageGroups.get(groupId);
        if (imagesInGroup == null) {
            imagesInGroup = new ArrayList<>();
        }
        imagesInGroup.add(image);
        imageGroups.put(groupId, imagesInGroup);
    }

    /**
     * Produces a fixed image from each group.
     * 
     * @return
     */
    private List<BufferedImage> getFixedImages() {

        List<BufferedImage> fixedImages = new ArrayList<>();

        for (Integer groupId : imageGroups.keySet()) {
            fixedImages.add(getFixedImage(groupId));
        }
        
        return fixedImages;
    }

    /**
     * Produces a fixed image from the given group.
     * 
     * @param groupId
     * @return
     */
    private BufferedImage getFixedImage(int groupId) {

        System.out.println("Fixing image: " + groupId);

        // Take the first image in the group
        List<BufferedImage> imagesInGroup = imageGroups.get(groupId);
        BufferedImage masterImage = imagesInGroup.get(0);
        imagesInGroup.remove(0);
        
        // Find all highlighted pixels
        for (int y = 0; y < masterImage.getHeight(); y++) {
            for (int x = 0; x < masterImage.getWidth(); x++) {
                
                if (masterImage.getRGB(x, y) == HIGHLIGHT_COLOUR) {
                    boolean foundReplacement = false;
                
                    // See if any other images in this group have a
                    // value for the equivalent pixel
                    for (BufferedImage image : imagesInGroup) {
                        int col = image.getRGB(x, y);
                        if (col != HIGHLIGHT_COLOUR) {
                            masterImage.setRGB(x, y, col);
                            foundReplacement = true;
                            break;
                        }
                    }
                    
                    if (!foundReplacement) {
                        System.out.println("No replacement found for pixel at " +
                                x + ", " + y);
                        masterImage.setRGB(x, y, BG_COLOUR);
                    }
                }
                
            }
        }
        
        return masterImage;
    }

    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Entry point for the application.
     * 
     * @param args
     */
    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Expected: SOURCE_FOLDER");
            System.exit(-1);
        }

        String imageDir = args[0];
        
        // Find all images in directory
        System.out.println("Finding files");
        File dir = new File(imageDir);
        File[] imageFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches(IMAGE_FILENAME_REGEX);
            }
        });

        if (imageFiles == null) {
            System.out.println("Invalid source directory: " +
                    dir.getAbsolutePath());
            System.exit(1);
        }
        
        if (imageFiles.length == 0) {
            System.out.println("No image files found in directory: " + 
                    dir.getAbsolutePath());
            System.exit(1);
        }

        SpriteFixer sf = new SpriteFixer();

        // Process each image
        for (File file : imageFiles) {
            
            BufferedImage image = null;

            // Read image
            try {
                System.out.println("Reading image: " + file);
                image = ImageIO.read(file);
            } catch (IOException ex) {
                System.out.println("Unable to read image");
                ex.printStackTrace();
                continue;
            }
            
            // Group images by similiarity
            System.out.println("Processing...");
            sf.add(image);
        }

        // Ensure "out" directory exists
        new File("out").mkdir();
        
        // Save fixed sprites
        int i = 0;
        for (BufferedImage image : sf.getFixedImages()) {
            
            String filename = "out/" + String.valueOf(i) + ".png";
            
            try {
                saveImage(image, filename);
            } catch (IOException e) {
                System.out.println("Unable to save image");
                e.printStackTrace();
            }
            
            i++;
        }
        
        System.out.println("Success!");
    }

    /**
     * Saves the given image to a PNG file, if it doesn't already exist.
     * 
     * @param image
     * @param filename
     * @throws IOException
     */
    private static void saveImage(BufferedImage image, String filename)
            throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            ImageIO.write(image, "PNG", file);
        }
    }

}
