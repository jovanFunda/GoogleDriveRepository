import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;

import java.io.*;
import java.util.*;

public class GoogleDriveRepository extends Repository {

    private Drive service; // Google drive's service
    private File usersFile;  // Users.json file
    private File rootFolder; // Repository's root folder
    
    static {
        RepositoryManager.setRepository(new GoogleDriveRepository());
    }

    @Override
    void Init(String rootDirectory) {

        try {
            service = getDriveService();
            users = new ArrayList<>();
            gson = new Gson();
            this.rootDirectory = rootDirectory;

            String folderName = rootDirectory.split("/")[rootDirectory.split("/").length - 1];

            FileList folder = service.files().list()
                    .setQ("mimeType='application/vnd.google-apps.folder' and name='" + folderName + "'")
                    .execute();

            if(folder.getFiles().size() > 0)
                rootFolder = folder.getFiles().get(0);
            else {
                // throw PathToRepositoryDoesntExist
            }

            FileList result = service.files().list()
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .setQ("'" + rootFolder.getId() + "' in parents and mimeType='application/json' and name='Users.json'")
                    .execute();

            if(result.getFiles().size() > 0)
                usersFile = result.getFiles().get(0);
            else {
                File fileMetadata = new File();
                fileMetadata.setName("Users.json");
                fileMetadata.setMimeType("application/json");

                service.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            service.files().get(usersFile.getId()).executeMediaAndDownloadTo(outputStream);
            InputStream in = new ByteArrayInputStream(outputStream.toByteArray());

            Scanner s = new Scanner(in).useDelimiter("\\A");
            String usersString = s.hasNext() ? s.next() : "";
            String[] parts = usersString.split("\n");

            for (String str: parts) {
                users.add(gson.fromJson(str, User.class));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void AddUser(String username, String password, Privilege privilege) {
        if(hasPrivilegeError("AddUser")) {
            // throws NoPrivilegeException
        }

        for (User u : users) {
            if (u.getUsername().equals(username)) {
                // throws UserAlreadyExistsException
            }
        }

        if(privilege == Privilege.ADMIN) {
            // throws OnlyOneAdminUserCanExistException
        }

        User user = new User(username, password, privilege);
        String json = gson.toJson(user);

//        try {
//            FileWriter writer = new FileWriter(file,true);
//            writer.append("\n" + json);
//            writer.close();
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            service.files().get(usersFile.getId()).executeMediaAndDownloadTo(outputStream);
            InputStream in = new ByteArrayInputStream(outputStream.toByteArray());

            Scanner s = new Scanner(in).useDelimiter("\\A");
            String usersString = s.hasNext() ? s.next() : "";
            usersString += "\n" + json;

        } catch (IOException e) {
            e.printStackTrace();
        }

        users.add(user);
    }

    @Override
    void CreateDirectory(String dirName) {
        if(hasPrivilegeError("CreateDirectory")) {
            try {
                throw new NoPrivilegeException("You have no privilege to create directory!");
            } catch (NoPrivilegeException e) {
                return;
            }
        }

        File fileMetadata = new File();
        fileMetadata.setName(dirName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(rootFolder.getId()));

        try {
            service.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    int CreateFile(String s) {
        return 0;
    }

    @Override
    int ListFiles() {
        return 0;
    }

    @Override
    void MoveFile(String s, String s1) {

    }

    @Override
    void DeleteFile(String s) {

    }

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "My project";

    /**
     * Global instance of the {@link FileDataStoreFactory}.
     */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global instance of the JSON factory.
     */
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this quickstart.
     * <p>
     * If modifying these scopes, delete your previously saved credentials at
     * ~/.credentials/calendar-java-quickstart
     */
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     *
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = GoogleDriveRepository.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                clientSecrets, SCOPES).setAccessType("offline").build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }

    /**
     * Build and return an authorized Calendar client service.
     *
     * @return an authorized Calendar client service
     * @throws IOException
     */
    public static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
