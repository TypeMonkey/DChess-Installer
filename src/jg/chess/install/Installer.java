package jg.chess.install;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import jg.chess.install.chesspieces.ChessPiecesGrabber;
import jg.chess.install.xmls.FXMLGrabber;


public class Installer {

  private static final int REQUIRED_ARGS = 1;
  private static final Set<String> CHESS_PIECES;
  private static final Set<String> FXML_FILES;
  private static final Set<String> JARS;
  
  static {
    HashSet<String> chessPieces = new HashSet<>();
    chessPieces.add("bishopBlack.png");
    chessPieces.add("bishopWhite.png");
    chessPieces.add("kingBlack.png");
    chessPieces.add("kingWhite.png");
    chessPieces.add("knightBlack.png");
    chessPieces.add("knightWhite.png");
    chessPieces.add("pawnBlack.png");
    chessPieces.add("pawnWhite.png");
    chessPieces.add("queenBlack.png");
    chessPieces.add("queenWhite.png");
    chessPieces.add("rookBlack.png");
    chessPieces.add("rookWhite.png");
    
    HashSet<String> fxmlFiles = new HashSet<>();
    fxmlFiles.add("GameBrowser.fxml");
    fxmlFiles.add("GameEntrance.fxml");
    fxmlFiles.add("GameScreen.fxml");
    
    HashSet<String> jars = new HashSet<>();
    jars.add("client.jar");
    jars.add("packr.jar");
    jars.add("hamcrest_core_1.3.jar");
    jars.add("junit4.jar");
    jars.add("netty.jar");
    jars.add("packr.jar");

    
    CHESS_PIECES = Collections.unmodifiableSet(chessPieces);
    FXML_FILES = Collections.unmodifiableSet(fxmlFiles);
    JARS = Collections.unmodifiableSet(jars);
  }
  
  private final File installDirectory;
  
  public Installer(String installDirectory) throws IllegalArgumentException{
    this.installDirectory = new File(installDirectory).getAbsoluteFile();
    
    
    if (!this.installDirectory.mkdirs() ||
        !this.installDirectory.isDirectory() || 
        !this.installDirectory.canRead() || 
        !this.installDirectory.canWrite()) {
      throw new IllegalArgumentException("Install directory not a directory, or cannot be written or read from");
    }
  }
  
  public void install() throws Exception{
    final OSType os = OSType.determineOS();
    if (os == null) {
      throw new IllegalStateException("Your OS isn't supported!");
    }
    
    System.out.println(">>>USER OS: "+os.getPlatform());
    
    final File jdkDest = new File(installDirectory, "jdk8Archive."+os.getArchiveType());
    jdkDest.createNewFile();
    System.out.println(">>>JDK Destination: "+jdkDest.getAbsolutePath());
    
    //Download JDK
    downloadJDK(os, jdkDest);
    
    //unpack JDK archive
    final File jdkDir = unpackJDK(jdkDest); //need to pass this on to packr
    
    //delete jdk archive
    System.out.println(">>>Deleting JDK Archive. Res? "+jdkDest.delete());
    
    //copy the game contents to the install directory
    File [] content = transferGameContents();
    
    //copy over game libraries
    tranferJars();
    
    //create nested install directory
    final File nestedInstallDir = new File(installDirectory, "dchess");
    nestedInstallDir.mkdirs();
    System.out.println(">>>Created nested install directory: "+nestedInstallDir.getAbsolutePath());
    
    //create the configuration json file
    makeJson(os, jdkDir, content, nestedInstallDir);
    
    //call packr from commandline
    ProcessBuilder process = new ProcessBuilder("java", "-jar", "packr.jar", "options.json");
    process.directory(installDirectory);
    
    System.out.println(">>>Executing packr....");
    Process activeProcess = process.start();
    
    
    Thread listenerThread = new Thread(new Runnable() {   
      @Override
      public void run() {
        final InputStream procInputStream = activeProcess.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(procInputStream));       
        try {
          String line = null;
          while ((line = reader.readLine()) != null) {
            System.out.println(line);
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    });
    
    listenerThread.start();
    activeProcess.waitFor();
    System.out.println(">>>packr finished packing!");
    
    //now, clean out all items in the install directory EXCEPT temp
    System.out.println(">>>Cleaning install directory");
    cleanInstall(nestedInstallDir, installDirectory);
    System.out.println(">>>Cleaned install directory");
    System.out.println(">>>DChess instalation done!");
  }

  private void cleanInstall(File exclude, File target) {
    for (File cur : target.listFiles()) {
      if (!cur.getAbsolutePath().equals(exclude.getAbsolutePath())) {
        if (cur.isDirectory()) {
          cleanInstall(exclude, cur);
          System.out.println(" ->DELETE DIR: "+cur.getAbsolutePath()+" | "+cur.delete());
        }
        else {
          System.out.println(" >DELETE FILE: "+cur.getAbsolutePath()+" | "+cur.delete());
        }
      }
      else {
        System.out.println("---NOT DELETING! "+cur.getAbsolutePath());
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  private File makeJson(OSType os, File jdkDir, File [] contents, File tempInstall) throws IOException, ParseException {
    File optionsJSON = new File(installDirectory, "options.json");
    
    //Read in json file
    FileReader jsonReader = new FileReader(optionsJSON);
    
    JSONParser jsonParser = new JSONParser();
    JSONObject jsonObject = (JSONObject) jsonParser.parse(jsonReader);
    jsonReader.close();
    
    JSONArray classPath = new JSONArray();
    JARS.stream().filter(x -> !x.equals("packr.jar")).forEach(x -> classPath.add(new File(installDirectory, x).getAbsolutePath()));
    
    JSONArray resources = new JSONArray();
    Arrays.stream(contents).forEach(x -> resources.add(x.getAbsolutePath()));
    
    jsonObject.put("platform", os.getPlatform());
    jsonObject.put("jdk", jdkDir.getAbsolutePath());
    jsonObject.put("executable", "DChess");
    jsonObject.put("classpath", classPath);
    jsonObject.put("resources", resources);
    jsonObject.put("output", tempInstall.getAbsolutePath());
    
    System.out.println("--->NEW JSON: "+jsonObject);
    
    FileWriter jsonWriter = new FileWriter(optionsJSON);
    jsonWriter.write(jsonObject.toJSONString());
    jsonWriter.flush();
    jsonWriter.close();
    System.out.println(">>>WROTE OUT NEW options.json");
    
    return optionsJSON;
  }
  
  /**
   * Copies required game libraries (JARs) into the install directory, 
   * as well as the client JAR itself
   * @throws IOException - if an IO error occurs
   */
  private void tranferJars() throws IOException{
    for (String jar : JARS) {
      File dest = new File(installDirectory, jar);
      dest.createNewFile();
      System.out.println(">>>Transferring JAR: "+jar+" to "+dest.getAbsolutePath());
      
      InputStream source = Installer.class.getResourceAsStream(jar);
      System.out.println("---> Exists? "+source+" | "+jar);
      FileOutputStream fileOutputStream = new FileOutputStream(dest);
      
      IOUtils.copy(source, fileOutputStream);
      
      fileOutputStream.close();
    }
  }
  
  /**
   * Copies game contents to the install directory
   * @return an array of all game content directories and files
   * @throws IOException - if an IO error occurs
   */
  private File[] transferGameContents() throws IOException {
    
    System.out.println(">>>Transferring config.txt!");
    //copy over the default config file
    File configDest = new File(installDirectory, "config.txt");
    InputStream configStream = getClass().getResourceAsStream("config.txt");
    FileOutputStream outputStream = new FileOutputStream(configDest);
    IOUtils.copy(configStream, outputStream);
    configStream.close();
    outputStream.flush();
    outputStream.close();
    System.out.println(">>>Transferred config.txt!");
    
    System.out.println(">>>Transferring options.json!");
    //copy over the config JSON file for packr
    File configJsonDest = new File(installDirectory, "options.json");
    InputStream jsonStream = getClass().getResourceAsStream("options.json");
    FileOutputStream jsonOutputStream = new FileOutputStream(configJsonDest);
    IOUtils.copy(jsonStream, jsonOutputStream);
    jsonStream.close();
    jsonOutputStream.flush();
    jsonOutputStream.close();
    System.out.println(">>>Transferred options.json!");
    
    System.out.println(">>>Transferring icon.png!");
    //copy over the app icon
    File appIconDest = new File(installDirectory, "icon.png");
    InputStream iconStream = getClass().getResourceAsStream("icon.png");
    FileOutputStream iconOutputStream = new FileOutputStream(appIconDest);
    IOUtils.copy(iconStream, iconOutputStream);
    iconStream.close();
    iconOutputStream.flush();
    iconOutputStream.close();
    System.out.println(">>>Transferred icon.png!");
    
    //content files and directories
    File [] contents = {new File(installDirectory, "chesspieces/"), new File(installDirectory, "xmls/"), configDest, configJsonDest, appIconDest};
    
    //made the directories
    for (File file : contents) {
      System.out.println("---MAKING DIR: "+file.mkdirs()+" for "+file.getAbsolutePath());
    }
    
    //copy chess pieces first
    for (String piece : CHESS_PIECES) {
      File dest = new File(contents[0], piece);
      System.out.println("---> CHESS PIECE DEST: "+dest);
      dest.createNewFile();
      
      InputStream source = ChessPiecesGrabber.grab(piece);
      System.out.println("---> null? "+source+" | "+piece);
      FileOutputStream fileOutputStream = new FileOutputStream(dest);
      
      IOUtils.copy(source, fileOutputStream);
      
      source.close();
      fileOutputStream.flush();
      fileOutputStream.close();
    }
    
    //copy xmls last
    for (String fxml : FXML_FILES) {
      File dest = new File(contents[1], fxml);
      System.out.println("---> FXML DEST: "+dest);
      dest.createNewFile();
      
      InputStream source = FXMLGrabber.grab(fxml);
      System.out.println("---> null? "+source+" | "+fxml);
      FileOutputStream fileOutputStream = new FileOutputStream(dest);
      
      IOUtils.copy(source, fileOutputStream);
      
      source.close();
      fileOutputStream.flush();
      fileOutputStream.close();
    }
    
    return contents;
  }

  /**
   * Unpacks the JDK archive to a folder called "jdk8unpack" in the install directory
   * @param jdkArchive - the location of the JDK archive
   * @return the location of the folder where the JDK was unpacked to
   * @throws ArchiveException 
   * @throws IOException
   */
  private File unpackJDK(File jdkArchive) throws IOException, ArchiveException{
    File jdkUnpackedLocation = new File(installDirectory, "jdk8unpack/");
    jdkUnpackedLocation.mkdir();
    
    System.out.println(">>> Unpacking JDK at: "+jdkUnpackedLocation.getAbsolutePath());
    
    ArchiveInputStream inputStream = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(new FileInputStream(jdkArchive)));

    ArchiveEntry entry = null;
    while((entry = inputStream.getNextEntry()) != null) {
      if (entry.isDirectory()) {
        Files.createDirectories(Paths.get(jdkUnpackedLocation.getAbsolutePath(), entry.getName()));
        System.out.println("----CREATING DIRS: "+entry.getName());
      }
      else {
        File entryDest = new File(jdkUnpackedLocation, entry.getName());
        System.out.println("---WRITING ENTRY "+entry.getName()+" TO "+entryDest.getAbsolutePath());
        
        entryDest.getParentFile().mkdirs();
        OutputStream entryOutputStream = Files.newOutputStream(entryDest.toPath());
        IOUtils.copy(inputStream, entryOutputStream);
      }
    }
    
    inputStream.close();
    
    System.out.println(">>> Unpacked JDK! Location: "+jdkUnpackedLocation.getAbsolutePath());
    
    return jdkUnpackedLocation.listFiles()[0];
  }
  
  /**
   * Downloads the JDK appropriate for the machine's OS
   * @param os - the OSType of this machine
   * @param jdkDest - where to place the JDK archive
   * @throws IOException - if an IO error occurs.
   */
  private void downloadJDK(OSType os, File jdkDest) throws IOException{
    //Step 1: Download JDK8 that matches OS
    
    URL website = new URL(os.getJdk8Link());
    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
    FileOutputStream fos = new FileOutputStream(jdkDest);
    
    System.out.println("---DOWNLOADING JDK 8 from link: "+os.getJdk8Link());
    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    System.out.println("---DOWNLOAD DONE!");
    
    //Write archive to disk
    fos.flush();
    fos.close();  
  }

  public static void main(String[] args) {   
    if (args != null && args.length == REQUIRED_ARGS) {
      try {
        Installer installer = new Installer(args[0]);
        installer.install();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    else {
      System.err.println("Installation directory needed!");
    }
  }

}
