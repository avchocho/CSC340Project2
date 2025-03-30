import java.io.*;
import java.net.*;


//manages TCP connections with client- new instance created for each client that joins. 
//welcome message, sends questions, recieves answers form client, final message, client disconnection. 


public class ClientHandler implements Runnable {
    private final Socket socket;
    private final int clientID;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket, int id) {
        this.socket = socket;
        this.clientID = id;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getClientID() {
        return clientID;
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String receiveAnswer() {
        try {
            out.println("Your Answer (A/B/C/D): ");
            return in.readLine();
        } catch (IOException e) {
            System.out.println("Client-" + clientID + " disconnected.");
            return null;
        }
    }

    @Override
    public void run() {
        sendMessage("Welcome to the Trivia Game, Client-" + clientID + "!");
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
