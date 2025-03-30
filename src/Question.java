//this class stores the components of each question. 
//breaks it down into different components. 

public class Question {
    private final String questionNumber;    // "Question 1"
    private final String questionText;      // "What did the Florida Man do?"
    private final String[] options;         // A–D options
    private final char correctAnswer;       // 'A', 'B', 'C', or 'D'

    public Question(String line) {
        // Split by pipe symbol: 3 parts → question, options, correct answer
        String[] parts = line.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid question format: " + line);
        }

        // Separate "Question X:" and actual question text
        String[] qParts = parts[0].split(":", 2);
        this.questionNumber = qParts[0].trim();         // e.g. "Question 1"
        this.questionText = qParts[1].trim();           // actual question text

        // Get 4 options, assuming they are comma-separated
        this.options = parts[1].split(",", 4);

        // Correct answer letter (A, B, C, D)
        this.correctAnswer = parts[2].trim().toUpperCase().charAt(0);
    }

    public String getQuestionNumber() {
        return questionNumber;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String[] getOptions() {
        return options;
    }

    public String getOption(char letter) {
        int index = letter - 'A';
        return (index >= 0 && index < options.length) ? options[index] : null;
    }

    public char getCorrectAnswer() {
        return correctAnswer;
    }
    
    public String getFormattedQuestion() {
        return questionText + "\nA. " + options[0] + "\nB. " + options[1] + "\nC. " + options[2] + "\nD. " + options[3];
    }

}

