import javax.swing.JOptionPane;

public class ClientWindowTest {
    public static void main(String[] args) {
        String serverIP = JOptionPane.showInputDialog("Enter server IP address:");
        int port = 1234; // fixed port the server is using

        new ClientWindow(serverIP, port);
    }
}
