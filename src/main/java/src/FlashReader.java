package src;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class FlashReader extends TimerTask {

    private final static String USB_FOLDER_PATH = "/media/";

    private static final String PATH_TO_STORAGE = "/home/minsk/device/";

    private final static int FOLDER_QTY = 7;

    private final static Set<String> imgTypes = new HashSet<>(Arrays.asList("jpg", "png"));

    public static void main(String[] args) {
        FlashReader flashReader = new FlashReader();
        new Timer().schedule(flashReader, 0, 1000);
    }

    @Override
    public void run() {
        usbInspectionTask();
    }

    private void usbInspectionTask() {
        try {
            inspectUsbFolder();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private void inspectUsbFolder() throws IOException {
        File usbDir =  new File(USB_FOLDER_PATH);
        if (usbDir.isDirectory()){
            List<File> usbs = Arrays.stream(usbDir.listFiles()).filter(File::isDirectory).collect(Collectors.toList());.
            usbs.forEach(usb -> {
                File[] files = usb.listFiles((file1, s) -> s.endsWith(".properties"));
                for (File file : files) {
                    try {
                        Map<String, File> fileMap = validateConfigData(file);
                        FlashReader.copyInstructionsOnPi(fileMap.get("instructions"));
                        FlashReader.copyImagesToPi(fileMap.get("images"));
                        FlashReader.copyConfigToPi(file);
                    } catch (DataValidationError | IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } else {
            throw new IOException("No usb media folder");
        }
    }

    private Map<String, File> validateConfigData(File file) throws DataValidationError, IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(file));
        if (properties.containsKey("device")
                && properties.containsKey("hash")
                && properties.containsKey("instructions")
                && properties.containsKey("images")){
            String instructionFilePath = file.getParent() + properties.getProperty("instructions");
            File instructions = new File(instructionFilePath);
            validateInstructions(instructions);
            String imagesFilePath = file.getParent() + properties.getProperty("images");
            File images = new File(instructionFilePath);
            validateImages(new File(imagesFilePath));
            Map<String, File> fileMap = new HashMap<>();
            fileMap.put("instructions", instructions);
            fileMap.put("images", images);
            return fileMap;
        }
        throw new DataValidationError("invalid config file");
    }

    private static void validateImages(File file) throws DataValidationError {
        if (file.isDirectory() && Objects.requireNonNull(file.listFiles()).length > 0){
            File[] images = file.listFiles();
            Set<String> imgSet = new HashSet<>();
            for (File image : images) {
                String[] parts = image.getName().split("\\.");
                if (parts.length == 2 && tryParseInt(parts[0]) && imgTypes.contains(parts[1])){
                    if(imgSet.contains(parts[0]))
                        throw new DataValidationError("duplicate image naming");
                    imgSet.add(parts[0]);
                } else {
                    throw new DataValidationError("invalid image naming");
                }
            }
            return;
        }
        throw new DataValidationError("invalid image file");
    }

    private static void validateInstructions(File file) throws DataValidationError {
        if(!file.isFile()){
            throw new DataValidationError("instructions not a file");
        }
        JSONObject instructions = readInstructionsFile(file);
        if (instructions == null)
            throw new DataValidationError("invalid instructions file");
    }

    private static void copyInstructionsOnPi(File newInstructions) throws IOException {
        File instructions = new File(PATH_TO_STORAGE + "instructions");
        if(instructions.exists()) {
            FileUtils.forceDelete(instructions);
            instructions = new File(PATH_TO_STORAGE + "instructions");
        }
        FileUtils.copyFile(newInstructions, instructions);
    }

    private static void copyImagesToPi(File newImages) throws IOException {
        File images = new File(PATH_TO_STORAGE + "images");
        if(images.isDirectory()) {
            FileUtils.cleanDirectory(images);
            FileUtils.copyDirectory(newImages, images);
        } else {
            FileUtils.forceMkdir(images);
            FileUtils.copyDirectory(newImages, images);
        }
    }

    private static void copyConfigToPi(File newConfig) throws IOException {
        File instructions = new File(PATH_TO_STORAGE + "config");
        if(instructions.exists()) {
            FileUtils.forceDelete(instructions);
            instructions = new File(PATH_TO_STORAGE + "config");
        }
        FileUtils.copyFile(newConfig, instructions);
    }

    private static JSONObject readInstructionsFile(File config) {
        JSONObject object;
        try {
            object = new JSONObject(Objects.requireNonNull(getResourceFileAsString(config)));
        } catch (JSONException | FileNotFoundException e){
            return null;
        }
        return object;
    }

    private static String getResourceFileAsString(File file) throws FileNotFoundException {
        InputStream is = new FileInputStream(file.getPath());
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
    }

    private static boolean tryParseInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
