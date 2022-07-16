import java.util.Random;
import java.util.UUID;

public class Game {
    public void setPlayerOne(String playerOne) {
        this.playerOne = playerOne;
    }

    private String playerOne;

    public void setPlayerTwo(String playerTwo) {
        this.playerTwo = playerTwo;
    }

    public Game() {

    }

    private String playerTwo;

    public void setBoard(String[][] board) {
        this.board = board;
    }

    public String[][] board = {
            {"E", "E","E"},
            {"E", "E","E"},
            {"E", "E","E"}
    };

    public void setTurn(String turn) {
        this.turn = turn;
    }

    private String turn;
    public String playerOneThreadID;
    public String playerTwoThreadID;

    public void setGameID(String gameID) {
        this.gameID = gameID;
    }

    private String gameID;

    public Game(String playerOne, String playerTwo) {
        this.gameID = UUID.randomUUID().toString();
        this.playerOne = playerOne;
        this.playerTwo = playerTwo;

        this.turn = playerOne; // ALATI KÄIB ESIMESENA P1
    }

    public String getPlayerOne() {
        return this.playerOne;
    }

    public String getPlayerTwo() {
        return this.playerTwo;
    }

    public String getPlayerOneThreadID() {
        return playerOneThreadID;
    }

    public String getPlayerTwoThreadID() {
        return playerTwoThreadID;
    }

    public String getGameID() {
        return gameID;
    }

    public String getTurn() {
        return turn;
    }

    public String getMarker() {
        if(turn.equals(playerOne)) {
            return "X";
        }
        return "O";
    }

    public boolean isMarkValid(int x, int y) {
        return board[x][y].equals("E");
    }

    public void markSquare(int x, int y) {
        String marker = getMarker();
        board[x][y] = marker;
    }

    // teisest projektist

    public String getBoardString() {
        String result ="";
        for (String[] strings : board) {
            for (String string : strings) {
                result += string + " | ";
            }
            result += "\n";
        }
        return result;
    }

    public String boardToDB() {
        String res = "";
        for (String[] strings : board) {
            for (String string : strings) {
                res = res + string;
            }
        }
        return res;
    }

    private boolean checkRow() {
        String marker = getMarker();
        for (int i = 0; i < 3; i++) {
            int counter = 0;
            for (int j = 0; j < 3; j++) {
                if (board[i][j].equals(marker)) {
                    counter++;
                }
            }
            if (counter == 3) {
                return true;
            }
        }
        return false;
    }

    private boolean checkColumn() {
        String marker = getMarker();
        for (int i = 0; i < 3; i++) {
            int counter = 0;
            for (int j = 0; j < 3; j++) {
                if (board[j][i].equals(marker)) {
                    counter++;
                }
            }
            if (counter == 3) {
                return true;
            }
        }
        return false;
    }

    private boolean checkLeftDiagonal() {
        String marker = getMarker();
        int counter = 0;
        for (int i = 0; i < 3; i++) {
            if (board[i][i].equals(marker)){
                counter++;
            }
            if (counter == 3) {
                return true;
            }
        }
        return false;
    }

    private boolean checkRightDiagonal() {
        String marker = getMarker();
        if (board[0][2].equals(marker)) {
            if (board[1][1].equals(marker)) {
                if (board[2][0].equals(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkBoard() {
        return checkRow() || checkColumn() || checkRightDiagonal() || checkLeftDiagonal();
    }

    public void changeTurn() {
        if (turn.equals(playerOne)) {
            turn = playerTwo;
            return;
        }
        if (turn.equals(playerTwo)) {
            turn = playerOne;
            return;
        }
    }

    public String getCurrentTurnThreadID() {
        if (turn.equals(playerOne)) {
            return playerOneThreadID;
        }
        return playerTwoThreadID;


    }


}
