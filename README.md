# CSC340Project2

## Multi-Player Trivia Game

### Overview
This project is a multi-player trivia game built using Java, allowing players to participate over a network. It utilizes TCP for guaranteed data communication (questions and scores) and UDP for fast, connectionless communication (buzzing in to answer). The game supports concurrency with multi-threading, GUI-based clients, and a centralized multi-threaded server.

### Objectives & Features
- Uses **multi-threading** to handle multiple players at once
- Sends questions, answers, and scores using **TCP**
- Sends fast buzz-in messages using **UDP**
- Includes a **Java Swing GUI** for each player
- Game includes **20 questions** with timers for buzzing and answering
- Tracks **scores**, with penalties for wrong or missed answers
- Players can **join or leave** during the game
- Shows **final scores and winner** at the end
- Admin can **remove a player** using a kill-switch command
  
### Requirements
Java Version 21

### Server Design `TriviaServer.java` & `ClientThread.java`
The server is designed as a multi-threaded Java application that handles all core logic for the trivia game. It uses **TCP** to manage reliable communication with each client, such as sending questions, receiving answers, and updating scores, and **UDP** to receive fast, connectionless buzz-in messages from players. For each connected client, the server spawns a **separate ClientThread** to handle its TCP session. A shared UDPBuzzThread listens for all buzz-ins and maintains a synchronized queue of clients in the order they buzzed. The server manages the entire game flow, including timers for buzz-in and answer phases, score tracking, inactivity handling (clients are auto-removed after missing two answers), and finally, printing and sending out the results after 20 questions. It also supports a **manual kill-switch command** for the server to remove a client at runtime.

### Client Design `ClientWindow.java`
The client is a **Java Swing-based GUI** application that connects to the server using TCP and sends buzz-in messages using UDP. The GUI displays the **current question, answer options**, a **"Poll"** button to buzz in, a **"Submit"** button to send answers, a **live score display**, and a **countdown timer**. When a question is received, the client can attempt to buzz in. If the server responds with an **ACK**, the client is allowed to answer within 10 seconds; otherwise, a **NAK** message is shown, and controls are disabled. The game provides real-time feedback of correct/wrong answers, timeout penalties. The client exits when the server ends the game or sends a kill-switch. Clients who join late are placed in a waiting state until the next question appears, ensuring a smooth mid-game join experience.

### Installation & How to Run
Run the program using jar files: 

    Server
    java -jar TriviaServer.jar

    Client
    java -jar ClientWindow.jar

