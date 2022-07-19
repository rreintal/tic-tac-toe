import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import org.apache.commons.codec.binary.Base64;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static InputValidator.emailValidator.*;

/* class to demonstrate use of Gmail list labels API */
public class application {
    /** Application name. */
    private static final String APPLICATION_NAME = "Tic-tac-toe game handler";
    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /** Directory to store authorization tokens for this application. */
    // TOKENS_DIRECTORY_PATH salvesta env muutujana.
    private static final String TOKENS_DIRECTORY_PATH = System.getenv("TOKENS");

    // DATAPASE_PATH salvesta env muutujana. (.txt fail)
    private static final String DATABASE_PATH = System.getenv("DATABASE");
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.MAIL_GOOGLE_COM);

    // CREDENTIALS_FILE_PATH salvesta env. muutujana (.json fail)
    private static final String CREDENTIALS_FILE_PATH = System.getenv("CREDENTIALS");
    private static final String user = "me";
    public static Gmail service;
    private static List<Game> games = new ArrayList<>();
    static {
        try {
            service = getService();
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        //returns an authorized Credential object.
        return credential;
    }

    public static Gmail getService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void main(String... args) throws IOException, GeneralSecurityException, MessagingException {
        System.out.println("Tic-Tac-Toe game handler is starting...");
        System.out.println("Loading games from DB.");
        loadGames();
        while (true) {
            List<Message> unreadMessages = fetchUnreadMessages();
            for (Message unreadMessage : unreadMessages) {
                request(unreadMessage);
                confirmation(unreadMessage);
                try {
                    turn(unreadMessage);
                }catch (Exception e) {
                    System.out.println("exception at turn()");
                }
                markAsRead(unreadMessage); // see viimasena
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Main functions
    // ------------------------------------------------------------------------------------------------------------

    public static void turn(Message message) throws IOException, MessagingException {
        if (doesGameIDExist(message)) {
            try {
                // kui siin tuleb exception siis saada invalidInputMessage
                if (isTurnValid(message)) {
                    System.out.println("Handling turn message.");
                    markTurn(message);
                    Game game = getGameFromMessage(message);
                    if (game.checkBoard()) {
                        // kui on võit
                        sendGameEndMessage(game, true);
                        game.changeTurn(); // see selleks, et sendEndGameMessage saadab message sellele, kelle turn praegu on!
                        sendGameEndMessage(game, false);
                        games.remove(game); // eemaldab mängu
                        removeGameFromDB(game);
                    }
                    if (!game.checkBoard()) {
                        // kui ei ole võit
                        game.changeTurn();
                        sendTurnRequest(message);
                        updateDB(game);
                    }
                } else {
                    sendInvalidInputMessage(message);
                }
            }
            catch (NumberFormatException e) {
                sendInvalidInputMessage(message);
            }
        }
    }

    public static void request(Message msg) throws MessagingException, IOException {
        if (isRequestMessage(msg)) {
            System.out.println("Handling request message.");
            String requestSender = getRequestSender(msg);
            String requestRecipient = getRequestRecipient(msg);
            sendRequest(requestRecipient, requestSender);
        }
    }

    public static void confirmation(Message msg) throws IOException, MessagingException {
        if (isConfirmationMessage(msg)) {
            System.out.println("Handling confirmation message.");
            String msgBodyText = msg.getSnippet();
            String playerOne = getPlayerOne(msgBodyText);
            String playerTwo = getPlayerTwo(msg); // on see kes kirja saatis!
            Game game = createGame(playerOne, playerTwo);
            games.add(game);
            sendPlayerOneNotify(playerOne, game);
            sendPlayerTwoNotify(playerTwo, game);
            sendFirstTurnMessage(game);
            System.out.println("Saving game to DB.");
            saveGameToDB(game);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // is/are related functions.
    // ------------------------------------------------------------------------------------------------------------

    public static boolean isTurnValid(Message message) throws IOException {
        Game game = getGameByID(getGameIDFromSubject(getSubject(message)));
        System.out.println("isCurrentPlayerTurn: " + isCurrentPlayerTurn(message, game));
        System.out.println("areValidCordinates: " + areValidCordinates(getCordinates(message)));
        System.out.println("isMarkCorrect: " + isMarkCorrect(game, getCordinates(message)));
        return isCurrentPlayerTurn(message, game) &&
                areValidCordinates(getCordinates(message)) &&
                isMarkCorrect(game, getCordinates(message))
                && isTurnMessage(message);
    }

    public static boolean isMarkCorrect(Game game, int[] xy) {
        return game.isMarkValid(xy[0], xy[1]);
    }

    public static boolean areValidCordinates(int[] xy) {
        int x = xy[0];
        int y = xy[1];
        return x >= 0 && x <= 2 && y >= 0 && y <= 2;
    }

    public static void markTurn(Message message) throws IOException {
        int[] xy = getCordinates(message);
        String gameID = getGameIDFromSubject(getSubject(message));
        Game game = getGameByID(gameID);
        game.markSquare(xy[0], xy[1]);
    }

    public static boolean isRequestMessage(Message message) {
        if (message != null) {
            return message.getSnippet().startsWith("Play with: ") && validate(message.getSnippet());
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Create message related functions
    // ------------------------------------------------------------------------------------------------------------

    public static MimeMessage createEmail(String toEmailAddress,
                                          String fromEmailAddress,
                                          String subject,
                                          String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(fromEmailAddress));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(toEmailAddress));
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }

    public static Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    public static MimeMessage createReplyMessage(String toEmailAddress,
                                                 String fromEmailAddress,
                                                 String subject,
                                                 String bodyText, String messageID) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(fromEmailAddress));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(toEmailAddress));
        email.setSubject(subject);
        email.setText(bodyText);
        email.setHeader("In-Reply-To", messageID);
        return email;

    }


    // ------------------------------------------------------------------------------------------------------------
    // Fetch message related functions
    // ------------------------------------------------------------------------------------------------------------

    public static List<Message> getAllMessages() {
        try {
            List<Message> messageList = service.users().messages().list(user).execute().getMessages(); // Siin olevad Messaged on ainult messageID ja threadID (content puudub!)

            List<Message> returnList = new ArrayList<>();
            for (Message message : messageList) {
                returnList.add(service.users().messages().get(user, message.getId()).execute());
            }
            return returnList; // Siin olevad Messaged on contentiga. .getPayLoad(), .getHeaders() jne
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }


    public static List<Message> fetchUnreadMessages() { // saan kõik messaged mis on UNREAD labeliga
        List<Message> unread = getAllMessages().stream().filter(x -> x.getLabelIds().contains("UNREAD")).collect(Collectors.toList());
        return unread;
    }


    // ------------------------------------------------------------------------------------------------------------
    // Database related functions  --------------------------------------------------------------------------------
    // ------------------------------------------------------------------------------------------------------------

    public static void removeGameFromDB(Game game) {
        try {
            // loe kogu fail ilma mänguta, mida vaja updateda
            List<String> data = new ArrayList<>();
            // BufferedReader reader = new BufferedReader(new FileReader(new File("src/main/java/database.txt")));
            BufferedReader reader = new BufferedReader(new FileReader(new File(DATABASE_PATH)));
            for (Object line : reader.lines().toArray()) {
                if (line instanceof String) {
                    if (((String) line).contains(game.getGameID())) {
                        continue;
                    }
                    data.add((String) line);
                }
            }
            reader.close();

            // kirjuta kogu PUNKT 1s saadud info faili
            // BufferedWriter writer = new BufferedWriter(new FileWriter(new File("src/main/java/database.txt")));
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(DATABASE_PATH)));
            for (String datum : data) {
                writer.write(datum + "\n");
            }
            writer.close();
        }
        catch (Exception e) {
            System.out.println("failed to update database");
        }

    }

    public static void loadGames() {
        try {
            // BufferedReader br = new BufferedReader(new FileReader(new File("src/main/java/database.txt")));
            BufferedReader br = new BufferedReader(new FileReader(new File(DATABASE_PATH)));
            for (Object line : br.lines().toArray()) {
                if (line instanceof String) {
                    games.add(getGameFromDBString( (String) line));
                }
            }

        }
        catch (Exception e) {
            System.out.println("failed to load games from database");
        }
    }

    public static Game getGameFromDBString(String line) {
        // fields is: p1, p2, board, turn, p1thread, p2thread, gameID
        String[] fields = line.split(";");
        Game game = new Game();
        game.setPlayerOne(fields[0]);
        game.setPlayerTwo(fields[1]);
        game.setBoard(getBoardFromDBString(fields[2]));
        game.setTurn(fields[3]);
        game.playerOneThreadID = fields[4];
        game.playerTwoThreadID = fields[5];
        game.setGameID(fields[6]);
        return game;
    }

    public static String[][] getBoardFromDBString(String string) {
        String[][] board = new String[3][3];
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                board[i][j] = String.valueOf(string.charAt(0));
                string = string.substring(1);
            }
        }
        return board;
    }
    public static void saveGameToDB(Game game) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(DATABASE_PATH), true));
            writer.write(game.getPlayerOne() + ";" +
                    game.getPlayerTwo() + ";" +
                    game.boardToDB() + ";" +
                    game.getTurn() + ";" +
                    game.getPlayerOneThreadID() + ";" +
                    game.getPlayerTwoThreadID() + ";" +
                    game.getGameID() + "\n");
            writer.close();
        }
        catch (Exception error) {
            System.out.println("failed to write data to db");
            System.out.println(error);
        }
    }

    public static void updateDB(Game game) {
        try {
            // loe kogu fail ilma mänguta, mida vaja updateda
            List<String> data = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new FileReader(new File(DATABASE_PATH)));
            for (Object line : reader.lines().toArray()) {
                if (line instanceof String) {
                    if (((String) line).contains(game.getGameID())) {
                        continue;
                    }
                    data.add((String) line);
                }
            }
            reader.close();
            // kirjuta kogu saadud info faili
            // BufferedWriter writer = new BufferedWriter(new FileWriter(new File("src/main/java/database.txt")));
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(DATABASE_PATH)));
            for (String datum : data) {
                writer.write(datum + "\n");
            }
            writer.close();

            // lisa mäng faili
            saveGameToDB(game);
        }
        catch (Exception e) {
            System.out.println("failed to update database");
        }

    }

    // ------------------------------------------------------------------------------------------------------------
    // Get information from message functions ---------------------------------------------------------------------
    // ------------------------------------------------------------------------------------------------------------

    public static boolean doesGameIDExist(Message msg) throws IOException {
        String subject = getSubject(msg);
        String gameID = getGameIDFromSubject(subject);
        for (Game game : games) {
            if (game.getGameID().equals(gameID)) {
                return true;
            }
        }
        return false;
    }

    public static Game getGameFromMessage(Message message) throws IOException {
        String gameID = getGameIDFromSubject(getSubject(message));
        Game game = getGameByID(gameID);
        return game;
    }

    public static int[] getCordinates(Message message) {
        String[] xy = message.getSnippet().substring(6,9).split(",");
        int x = Integer.parseInt(xy[0]);
        int y = Integer.parseInt(xy[1]);
        return new int[]{x, y};
    }

    public static boolean isCurrentPlayerTurn(Message message, Game game) {
        String mailSender = getRequestSender(message);
        return game.getTurn().equals(mailSender);
    }

    public static String getGameIDFromSubject(String string) {
        return string.replaceAll("Re: ", "").substring(3); // replace("Re: ") juhul kui gmail ise lisab ja substring 3 kuna subject algab Pn:GAMEID
    }

    public static String getSubject(Message message) throws IOException {
        String result = "";
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.toPrettyString().contains("Subject")) {
                String subject = header.toString().replace("{\"name\":\"Subject\",\"value\":\"", "");
                result = subject.substring(0, subject.length() - 2);
            }
        }
        return result;
    }

    public static boolean isTurnMessage(Message msg) {
        return msg.getSnippet().startsWith("Mark:");
    }

    public static String getRequestRecipient(Message message) {
        return getMail(message.getSnippet());
    }

    public static String getPlayerTwo(Message message) {
        String mailAddress = "";
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.toString().contains("{\"name\":\"From\",\"value\"")) {
                Pattern pattern = Pattern.compile("[\\w.]+@[\\w.]+");
                Matcher matcher = pattern.matcher(header.toString());
                if (matcher.find()){
                    mailAddress = matcher.group();
                }
            }
        }
        return mailAddress;
    }

    public static String getPlayerOne(String msgText) {
        Matcher m = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+").matcher(msgText);
        List<String> emails = new ArrayList<>();
        while (m.find()) {
            emails.add(m.group());
        }
        return emails.get(1);
    }

    public static String getMessageID(Message message) throws IOException {
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.toPrettyString().contains("Message-Id") || header.toPrettyString().contains("Message-ID")) { // kuna Mail ja Reply-Mail JSON-is on messageID erinevalt
                return extractMessageID(header.toPrettyString());
            }
        }
        return null;
    }

    public static boolean isConfirmationMessage(Message msg) throws IOException {
        // kui threadis on täpselt 2 sõnumit ja snippet algab "OK"
        if (service.users().threads().get(user, msg.getThreadId()).size() == 2 && msg.getSnippet().startsWith("OK")) {
            return true;
        }
        return false;
    }

    public static String getRequestSender(Message message) {
        String mailAddress = "";
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.toString().contains("{\"name\":\"From\",\"value\"")) {
                Pattern pattern = Pattern.compile("[\\w.]+@[\\w.]+");
                Matcher matcher = pattern.matcher(header.toString());
                if (matcher.find()){
                    mailAddress = matcher.group();
                }
            }
        }
        return mailAddress;
    }

    public static String extractMessageID(String jsonEntry) {
        Matcher m = Pattern.compile("<.+>").matcher(jsonEntry);
        String res = null;
        while (m.find()) {
            res = m.group();
        }
        return res;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Send message related functions
    // ------------------------------------------------------------------------------------------------------------

    public static void sendRequest(String toWho, String fromWho) throws MessagingException, IOException {
        MimeMessage content = createEmail(toWho, user, "Game request. " + getCurrentTimeString(), fromWho + " wishes to play tic-tac-toe with you. Reply with OK to start game.");
        Message message = createMessageWithEmail(content);
        service.users().messages().send(user, message).execute();
        System.out.println("Sending message to: " + toWho);
    }

    public static void sendPlayerTwoNotify(String mailAddress, Game game) throws MessagingException, IOException {
        String gameInstructions = "To place a marker reply with message: Mark(x,y)\nFor example: Mark:(1,2)";
        String bodyText = gameInstructions + "\n" + "Game is starting now!" + "\n" + "Game ID:" + game.getGameID();
        String subject = "P2:" + game.getGameID();
        Message msg = createMessageWithEmail(createEmail(mailAddress, user, subject, bodyText));
        game.playerTwoThreadID = service.users().messages().send(user, msg).execute().getThreadId();
        System.out.println("Sending message to: " + mailAddress);
    }

    public static void sendPlayerOneNotify(String mailAddress, Game game) throws MessagingException, IOException {
        String gameInstructions = "To place a marker reply with message: Mark(x,y) \n For example: Mark(1,2)";
        String bodyText = gameInstructions + "\n" + "Game is starting now!" + "\n" + "Game ID:" + game.getGameID();
        String subject = "P1:" + game.getGameID();
        Message msg = createMessageWithEmail(createEmail(mailAddress, user, subject, bodyText));
        game.playerOneThreadID = service.users().messages().send(user, msg).execute().getThreadId();
        System.out.println("Sending message to: " + mailAddress);
    }

    public static void sendFirstTurnMessage(Game game) throws MessagingException, IOException {
        String bodyText = "Your turn!" + "\n" + game.getBoardString() + "\n" +getCurrentTimeString();
        String subject = "P1:" + game.getGameID();
        Message threadLastMessage = service.users().threads().get(user, game.playerOneThreadID).execute().getMessages().get(0);
        String messageID = getMessageID(threadLastMessage);
        Message msg = createMessageWithEmail(createReplyMessage(game.getPlayerOne(), user, subject, bodyText, messageID)).setThreadId(game.playerOneThreadID);
        service.users().messages().send(user, msg).execute();
        System.out.println("Sending message to: " + game.getPlayerOne());
    }

    public static void sendInvalidInputMessage(Message message) throws MessagingException, IOException {
        String toWho = getRequestSender(message);
        String subject = getSubject(message);
        String threadID = message.getThreadId();
        String currentTimeString = getCurrentTimeString();
        String bodyText = "Wrong input!" +
                "\n" +
                "Try again," +
                "\n" +
                currentTimeString;
        String messageID = getMessageID(message);
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.toPrettyString().contains("Message-ID")) {
                System.out.println(header.toPrettyString());
            }
        }
        Message msg = createMessageWithEmail(createReplyMessage(toWho, user, subject, bodyText, messageID)).setThreadId(threadID);
        service.users().messages().send(user, msg).execute();
        System.out.println("Sent invalid input message to " + toWho);

    }

    public static void sendGameEndMessage(Game game, boolean winner) throws IOException, MessagingException {
        String result;
        if (winner) {
            result = "You won!";
        }else {
            result = "You lost!";
        }
        Message threadLastMessage = service.users().threads().get(user, game.getCurrentTurnThreadID()).execute().getMessages().get(0);
        String messageID = getMessageID(threadLastMessage);
        Message msg = createMessageWithEmail(createReplyMessage(game.getTurn(), user, getSubject(threadLastMessage), game.getBoardString() + "\n" + result, messageID))
                .setThreadId(game.getCurrentTurnThreadID());
        service.users().messages().send(user, msg).execute();

    }

    public static void sendTurnRequest(Message message) throws IOException, MessagingException {
        Game game = getGameFromMessage(message);
        Message threadLastMessage = service.users().threads().get(user, game.getCurrentTurnThreadID()).execute().getMessages().get(0);
        String messageID = getMessageID(threadLastMessage);
        Message msg = createMessageWithEmail(createReplyMessage(game.getTurn(), user, getSubject(threadLastMessage), "Your turn!" + "\n" + game.getBoardString() + "\n" +getCurrentTimeString(), messageID))
                .setThreadId(game.getCurrentTurnThreadID());
        service.users().messages().send(user, msg).execute();
    }
    // ------------------------------------------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------------------------------------------

    public static Game getGameByID(String gameID) {
        for (Game game : games) {
            if (game.getGameID().equals(gameID)) {
                return game;
            }
        }
        return null;
    }

    public static void markAsRead(Message message) throws IOException {
        service.users().messages().modify(user, message.getId(), new ModifyMessageRequest().setRemoveLabelIds(List.of("UNREAD"))).execute();
    }

    public static Game createGame(String playerOne, String playerTwo) {
        return new Game(playerOne, playerTwo);
    }

    public static String getCurrentTimeString() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }
}