import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TriviaServer {
    private static final int PORT = 1234;
    private static final List<Question> questions = new ArrayList<>();
    private static final List<ClientThread> clients = new ArrayList<>();
    private static final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();

    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private static int currentQuestionIndex = 0;
    private static boolean receivingPoll = true;
    private static boolean hasPrintedWinners = false;

    public static void main(String[] args) {
        loadQuestions();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("📱 Trivia Server started on port " + PORT);

            UDPThread udpThread = new UDPThread();
            udpThread.start();

            // Accept clients continuously in a background thread
            new Thread(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientThread handler = new ClientThread(clientSocket, clients.size());

                        if (currentQuestionIndex > 0) {
                            handler.setJoinedMidGame(true);
                            handler.sendMessage("WaitForNextRound");
                        }

                        clients.add(handler);
                        pool.execute(handler);
                        System.out.println("Client-" + handler.getClientID() + " connected.");
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }).start();

            // Wait until at least 2 clients connect before starting the game
            while (clients.size() < 2) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Starting Trivia Game!");
            sendNextQuestionToAll();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendNextQuestionToAll() throws IOException {
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }

        receivingPoll = true;
        messageQueue.clear();
        Question q = questions.get(currentQuestionIndex);
        System.out.println("Sending Question " + (currentQuestionIndex + 1));

        for (ClientThread client : clients) {
            client.setCanAnswer(false); // Wait for UDP buzz
            client.setCorrectAnswer(String.valueOf(q.getCorrectAnswer()));
            client.sendMessage(q.getQuestionNumber() + ":");
            client.sendMessage(q.getQuestionText());
            client.sendMessage("A. " + q.getOptions()[0]);
            client.sendMessage("B. " + q.getOptions()[1]);
            client.sendMessage("C. " + q.getOptions()[2]);
            client.sendMessage("D. " + q.getOptions()[3]);
            client.sendMessage("Your Answer:");
        }

        currentQuestionIndex++;
    }

    public static void moveAllToNextQuestion() throws IOException {
        sendNextQuestionToAll();
    }

    public static void removeClient(ClientThread client) throws IOException {
        clients.remove(client);
        client.close();
    }

    public static void clientOutOfTime(ClientThread client) throws IOException {
        System.out.println("Client-" + client.getClientID() + " ran out of time!");
        client.sendMessage("Time's up! -20 points.");
        client.setCanAnswer(false);
        client.decreaseScore(20);
        client.sendMessage("score " + client.getScore());

        moveAllToNextQuestion();
    }

    private static void endGame() throws IOException {
        if (hasPrintedWinners) return;
        hasPrintedWinners = true;

        System.out.println("Game Over. Final Scores:");
        clients.sort((a, b) -> b.getScore() - a.getScore());
        System.out.println("Winner: " + clients.get(0).getClientID() + " with " + clients.get(0).getScore());

        for (ClientThread client : clients) {
            client.sendMessage("Game Over! Your final score: " + client.getScore());
            System.out.println(client.getClientID() + ": " + client.getScore());
            client.close();
        }

        pool.shutdown();
    }

    private static void loadQuestions() {
        try (BufferedReader br = new BufferedReader(new FileReader("Questions.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                questions.add(new Question(line));
            }
        } catch (IOException e) {
            System.err.println("⚠ Could not load questions.");
            e.printStackTrace();
        }
    }

    public static int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    // === UDP Buzzer Thread ===
    private static class UDPThread extends Thread {
        private DatagramSocket socket;
        private final byte[] buffer = new byte[256];

        public UDPThread() throws SocketException {
            socket = new DatagramSocket(PORT);
        }

        public void run() {
            System.out.println("UDP Buzzer Listener running...");
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    InetAddress address = packet.getAddress();

                    System.out.println("📨 UDP BUZZ from " + address.getHostAddress());

                    if (receivingPoll && message.equalsIgnoreCase("buzz")) {
                        receivingPoll = false;

                        ClientThread winner = findClientByAddress(address);
                        if (winner != null) {
                            winner.setCanAnswer(true);
                            winner.sendMessage("ACK — You may answer!");
                            winner.sendMessage("Time 10");
                        } else {
                            System.out.println("⚠ No TCP match found for UDP packet.");
                        }
                    } else {
                        ClientThread lateClient = findClientByAddress(address);
                        if (lateClient != null) {
                            lateClient.sendMessage("NAK — Too late!");
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private ClientThread findClientByAddress(InetAddress address) {
            for (ClientThread client : clients) {
                if (client.getSocket().getInetAddress().equals(address)) {
                    return client;
                }
            }
            return null;
        }
    }
}
