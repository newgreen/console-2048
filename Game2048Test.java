import java.io.*;
import java.util.Arrays;

public class Game2048Test {
    public static void main(String[] args) throws IOException {
        Game2048Test test = new Game2048Test(4, 0.75);
        test.run();
    }
    
    private int column;
    private Game2048Core game;
    
    private String status;
    private int[][] history;
    private int historyLength = 0;
    private int replayStep = 0;
    private String lastCmd = "";
    
    public Game2048Test(int column, double randomFactorOf2) {
        this.column = column;
        status = "play";
        game = new Game2048Core(column, randomFactorOf2);
        showGameFrame(game.getGridsContent());
    }
    
    public void run() throws IOException {
        Command cmd = readCommand();
        while (cmd != Command.EXIT) {
            executeCommand(cmd);
            cmd = readCommand();
        }
    }
    
    void showGameFrame(int[] grids) {
        System.out.println(gameFrameToStr(grids));
    }
    
    String expandStr(String str, int length) {
        if (str.equals(" ")) {
            return String.format("%" + length + "s", "");
        } else {
            return String.format("%" + length + "s", "").replace(" ", str);
        }
    }
    
    String numberToStr(int number, int maxNumLen) {
        String strNumber = "";
        
        if (number != 0) {
            strNumber += number;
        } else {
            strNumber += " ";
        }
        
        int left = (maxNumLen - strNumber.length()) / 2;
        int right = maxNumLen - left - strNumber.length();
        return expandStr(" ", left) + strNumber + expandStr(" ", right);
    }
    
    String gameFrameToStr(int[] grids) {
        int maxNum = Arrays.stream(grids).max().getAsInt();
        int maxNumLen = (int)(Math.log10(maxNum > 0 ? maxNum : 1) + 1);
        
        int maxGridLen = (maxNumLen + 3) * column + 1;
        int headLineLen = headLineToStr(0).length();
        
        maxNumLen = (Math.max(maxGridLen, headLineLen) + column - 2) / column - 3;
        int maxFrameLen = (maxNumLen + 3) * column + 1;
        
        String borderOutter = expandStr("=", maxFrameLen);
        String borderInner = expandStr("-", maxFrameLen);
        String headLine = headLineToStr(maxFrameLen);
        
        String result = borderOutter + "\n" + headLine + "\n" + borderOutter + "\n";
        
        for (int r = 0, gridIndex = 0; r < column; r++) {
            if (r > 0) {
                result += borderInner + "\n";
            }
            
            for (int c = 0; c < column; c++) {
                result += "| " + numberToStr(grids[gridIndex++], maxNumLen) + " ";
            }
            result += "|\n";
        }
        
        result += borderOutter;
        return result;
    }
    
    String headLineToStr(int lineLength) {
        String result = String.format("| status: %s | score: %d | step: ", status, game.getScore());
        if (status.equals("play")) {
            result += String.format("%d ", game.getActionCount());
        } else {
            result += String.format("%d/%d ", replayStep, game.getActionCount());;
        }
        
        if (result.length() + 1 < lineLength) {
            result += String.format("%" + (lineLength - result.length() - 1) + "s", "");
        }
        
        return result + "|";
    }
    
    private enum Command {
        /* play   */ UP,   DOWN, LEFT, RIGHT,
        /* replay */ PREV, NEXT, SET,
        /* common */ EXIT, SWITH
    }
    
    public void executeCommand(Command cmd) {
        boolean changed = false;
        
        switch (cmd) {
            case UP:
                changed = game.doAction(Game2048Core.Direction.UP);
                break;
            
            case DOWN:
                changed = game.doAction(Game2048Core.Direction.DOWN);
                break;
            
            case LEFT:
                changed = game.doAction(Game2048Core.Direction.LEFT);
                break;
            
            case RIGHT:
                changed = game.doAction(Game2048Core.Direction.RIGHT);
                break;
            
            case PREV:
                if (replayStep > 0) {
                    replayStep--;
                    changed = true;
                }
                break;
            
            case NEXT:
                if (replayStep < history.length - 1) {
                    replayStep++;
                    changed = true;
                }
                break;
            
            case SET:
                changed = true;
                break;
            
            case SWITH:
                switchGameMode();
                changed = true;
                break;
        }
        
        if (changed) {
            showGameFrame(status.equals("play") ? game.getGridsContent() : history[replayStep]);
        }
    }
    
    private void switchGameMode() {
        if (status.equals("play")) {
            switchFromPlay();
        } else {
            switchFromReplay();
        }
    }
    
    private Command readCommand() throws IOException {
        if (status.equals("play")) {
            return readPlayCommand();
        } else {
            return readReplayCommand();
        }
    }
    
    private void switchFromPlay() {
        history = game.getHistory();
        historyLength = game.getActionCount() + 1;
        replayStep = 0;
        status = "replay";
    }
    
    private void switchFromReplay() {
        history = null;
        replayStep = 0;
        status = "play";
    }
    
    private Command readPlayCommand() throws IOException {
        while (status.equals("play")) {
            String cmd = readRawCommand();
            if (cmd.equalsIgnoreCase("U") || cmd.equalsIgnoreCase("up")) {
                return Command.UP;
            } else if (cmd.equalsIgnoreCase("D") || cmd.equalsIgnoreCase("down")) {
                return Command.DOWN;
            } else if (cmd.equalsIgnoreCase("L") || cmd.equalsIgnoreCase("left")) {
                return Command.LEFT;
            } else if (cmd.equalsIgnoreCase("R") || cmd.equalsIgnoreCase("right")) {
                return Command.RIGHT;
            } else if (cmd.equalsIgnoreCase("exit") || cmd.equalsIgnoreCase("quit") || cmd.equalsIgnoreCase("q")) {
                return Command.EXIT;
            } else if (cmd.equalsIgnoreCase("switch")) {
                return Command.SWITH;
            } else {
                System.out.println("invalid command: " + cmd);
                lastCmd = "";
            }
        }
        
        return Command.SWITH;
    }
    
    private Command readReplayCommand() throws IOException {
        while (status.equals("replay")) {
            String cmd = readRawCommand();
            if (cmd.equalsIgnoreCase("P") || cmd.equalsIgnoreCase("prev")) {
                return Command.PREV;
            } else if (cmd.equalsIgnoreCase("N") || cmd.equalsIgnoreCase("next")) {
                return Command.NEXT;
            } else if (cmd.startsWith("set ") || cmd.startsWith("SET ") ||
                       cmd.startsWith("s ")   || cmd.startsWith("S "))
            {
                replayStep = Integer.parseInt(cmd.substring(cmd.indexOf(" ") + 1));
                return Command.SET;
            } else if (cmd.equalsIgnoreCase("exit") || cmd.equalsIgnoreCase("quit") || cmd.equalsIgnoreCase("q")) {
                return Command.EXIT;
            } else if (cmd.equalsIgnoreCase("switch")) {
                return Command.SWITH;
            } else {
                System.out.println("invalid command: " + cmd);
                lastCmd = "";
            }
        }
        
        return Command.SWITH;
    }
    
    private String readRawCommand() throws IOException {
        do {
            System.out.print(status + ":/> ");
            
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            String cmd = input.readLine().trim();
            if (cmd.equals("")) {
                if (!lastCmd.equals("")) {
                    cmd = lastCmd;
                    return cmd;
                }
                continue;
            }
            
            lastCmd = cmd;
            return cmd;
        } while (true);
    }
}
