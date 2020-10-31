import java.util.Arrays;
import java.util.Random;

public class Game2048Core {
    private int column;
    private double randomFactorOf2;
    
    private int[] grids;
    private int score;
    
    private Action action;
    
    private static final int HISTORY_EXPAND_LENGTH = 1024;
    private byte[] gridNumberHistory;
    private int gridNumberHistoryCount;
    private byte[] actionHistory;
    private int actionHistoryCount;
    
    private static final int INIT_GRID_CNT = 2;
    
    public enum Direction {
        LEFT, RIGHT, UP, DOWN
    }
    
    public Game2048Core(int column, double randomFactorOf2) {
        this.column = column;
        this.randomFactorOf2 = randomFactorOf2;
        
        action = new Action(column);
        
        initHistory();
        initGrids();
    }
    
    public int[] getGridsContent() {
        return grids.clone();
    }
    
    public int getScore() {
        return score;
    }
    
    public int getActionCount() {
        return actionHistoryCount;
    }
    
    public double getRandomFactorOf2() {
        return randomFactorOf2;
    }
    
    public boolean doAction(Direction dir) {
        ActionResult result = action.execute(grids, dir);
        if (result.changed) {
            recordAction(dir);
            addGridNumber();
            score += result.score;
        }
        return result.changed;
    }
    
    public boolean isGameOver() {
        int[] tmpGrids = grids.clone();
        for (int i = 0; i < Direction.values().length; i++) {
            if (action.execute(tmpGrids, Direction.values()[i]).changed) {
                return false;
            }
        }
        return true;
    }
    
    public int[][] getHistory() {
        // plus 1 because of first grids before any action
        int[][] history = new int[actionHistoryCount + 1][column * column];
        int historyIndex = 0;
        
        int[] replayGrids = new int[column * column];
        
        // generate the first grids before any action
        for (int i = 0; i < INIT_GRID_CNT; i++) {
            addGridNumber(replayGrids, GridNumber.decode(gridNumberHistory[i]));
        }
        System.arraycopy(replayGrids, 0, history[historyIndex++], 0, replayGrids.length);
        
        // replay actions
        for (int i = 0; i < actionHistoryCount; i++) {
            action.execute(replayGrids, Direction.values()[actionHistory[i]]);
            addGridNumber(replayGrids, GridNumber.decode(gridNumberHistory[i + INIT_GRID_CNT]));
            System.arraycopy(replayGrids, 0, history[historyIndex++], 0, replayGrids.length);
        }
        
        // after replayed, replayGrids should be same with grids or bugs somewhere
        for (int i = 0; i < replayGrids.length; i++) {
            assert(replayGrids[i] == grids[i]);
        }
        
        return history;
    }
    
    private void addGridNumber(int[] grids, GridNumber gridNum) {
        grids[gridNum.location] = gridNum.number;
    }
    
    private void addGridNumber() {
        GridNumber gridNum = new GridNumber(grids, randomFactorOf2);
        if (gridNum.number != 0) {
            assert(gridNum.location >= 0);
            assert(gridNum.location < column * column);
            assert(grids[gridNum.location] == 0);
            
            addGridNumber(grids, gridNum);
            recordGridNumber(gridNum);
        }
    }
    
    private void initGrids() {
        grids = new int[column * column];
        for (int i = 0; i < INIT_GRID_CNT; i++) {
            addGridNumber();
        }
    }
    
    // Note: init history before calling addGridNumber
    private void initHistory() {
        gridNumberHistoryCount = 0;
        gridNumberHistory = new byte[HISTORY_EXPAND_LENGTH];
        
        actionHistoryCount = 0;
        actionHistory = new byte[HISTORY_EXPAND_LENGTH];
    }
    
    private void recordGridNumber(GridNumber gridNum) {
        if (gridNumberHistoryCount == gridNumberHistory.length) {
            gridNumberHistory = Arrays.copyOf(gridNumberHistory, gridNumberHistory.length + HISTORY_EXPAND_LENGTH);
        }
        
        gridNumberHistory[gridNumberHistoryCount++] = gridNum.encode();
    }
    
    private void recordAction(Direction dir) {
        if (actionHistoryCount == actionHistory.length) {
            actionHistory = Arrays.copyOf(actionHistory, actionHistory.length + HISTORY_EXPAND_LENGTH);
        }
        
        actionHistory[actionHistoryCount++] = (byte)dir.ordinal();
    }
}

class GridNumber {
    public int location;
    public int number;
    public int gridsLength;
    
    public GridNumber(int[] grids, double randomFactorOf2) {
        int[] idleLocation = new int[grids.length];
        int idleCnt = 0;
        
        for (int i = 0; i < grids.length; i++) {
            if (grids[i] == 0) {
                idleLocation[idleCnt++] = i;
            }
        }
        
        if (idleCnt > 0) {
            Random random = new Random();
            location = idleLocation[random.nextInt(idleCnt)];
            number = Math.random() > randomFactorOf2 ? 4 : 2;
            gridsLength = grids.length;
        }
    }
    
    private GridNumber(int location, int number) {
        this.location = location;
        this.number = number;
    }
    
    private static final int MAX_LOCATION = 0x3F;
    
    public byte encode() {
        assert(gridsLength <= MAX_LOCATION + 1);
        return (byte)((((number == 4) ? 1 : 0) << 6) | (location & MAX_LOCATION));
    }
    
    public static GridNumber decode(byte code) {
        int tmpCode = code;
        return new GridNumber(tmpCode & MAX_LOCATION, (((tmpCode >> 6) & 0x1) == 1) ? 4 : 2);
    }
}

class ActionResult {
    public boolean changed;
    public int score;
}

class Action {
    public Action(int column) {
        initIndexMatrix(column);
    }
    
    public ActionResult execute(int[] grids, Game2048Core.Direction dir) {
        ActionResult result = new ActionResult();
        
        int[][] indexMatrix = getIndexByDirection(dir);
        for (int i = 0; i < indexMatrix.length; i++) {
            executeOnSingleLine(grids, indexMatrix[i], result);
        }
        
        return result;
    }
    
    private void executeOnSingleLine(int[] grids, int[] index, ActionResult result) {
        int[] line = new int[index.length];
        int cnt = 0;
        
        // fetch non-zero numbers
        for (int i = 0; i < index.length; i++) {
            if (grids[index[i]] != 0) {
                line[cnt++] = grids[index[i]];
            }
        }
        
        boolean moved = false;
        for (int i = 0; i < index.length; i++) {
            if (grids[index[i]] != line[i]) {
                moved = true;
                break;
            }
        }
        
        // merge same numbers and stat score if possible
        int score = 0;
        boolean merged = false;
        
        for (int i = 1; i < cnt; i++) {
            if (line[i-1] == line[i]) {
                line[i-1] += line[i];
                score += line[i-1];
                merged = true;
                
                // behind line[i], move every number one step
                for (int j = i + 1; j < cnt; j++) {
                    line[j - 1] = line[j];
                }
                
                /* after merging, cnt should be reduced, 
                 * and the last number should be cleared which has been moved */
                line[--cnt] = 0;
            }
        }
        
        // update grids
        for (int i = 0; i < index.length; i++) {
            grids[index[i]] = line[i];
        }
        
        if (moved || merged) {
            result.score += score;
            result.changed = true;
        }
    }
    
    private int[][] indexForLeft  = null;
    private int[][] indexForRight = null;
    private int[][] indexForUp    = null;
    private int[][] indexForDown  = null;
    
    private void initIndexMatrix(int column) {
        /** index for left
         *   0,  1,  2,  3,
         *   4,  5,  6,  7,
         *   8,  9, 10, 11,
         *  12, 13, 14, 15,
         */
        indexForLeft = new int[column][column];
        for (int r = 0; r < column; r++) {
            for (int c = 0; c < column; c++) {
                indexForLeft[r][c] = r * column + c;
            }
        }
        
        /** index for right
         *   3,  2,  1,  0,
         *   7,  6,  5,  4,
         *  11, 10,  9,  8,
         *  15, 14, 13, 12,
         *
         *  Note: 1st row from-left-to-right: (r+1)*column-(1,2,3,4)
         */
        indexForRight = new int[column][column];
        for (int r = 0; r < column; r++) {
            for (int c = 0; c < column; c++) {
                indexForRight[r][c] = (r + 1) * column - (c + 1);
            }
        }
        
        /** index for up
         *  0, 4,  8, 12,
         *  1, 5,  9, 13,
         *  2, 6, 10, 14,
         *  3, 7, 11, 15,
         *
         * Note: 1st column from-up-to-down: c*column+(0,1,2,3)
         */
        indexForUp = new int[column][column];
        for (int r = 0; r < column; r++) {
            for (int c = 0; c < column; c++) {
                indexForUp[r][c] = c * column + r;
            }
        }
        
        /** index for down
         *  12,  8, 4, 0,
         *  13,  9, 5, 1,
         *  14, 10, 6, 2,
         *  15, 11, 7, 3,
         *
         * Note: 4th column from-up-to-down: (column-c-1)*column+(0,1,2,3)
         */
        indexForDown = new int[column][column];
        for (int r = 0; r < column; r++) {
            for (int c = 0; c < column; c++) {
                indexForDown[r][c] = (column - c - 1) * column + r;
            }
        }
    }
    
    private int[][] getIndexByDirection(Game2048Core.Direction dir) {
        switch (dir) {
            case LEFT:
                return indexForLeft;
            case RIGHT:
                return indexForRight;
            case UP:
                return indexForUp;
            case DOWN:
                return indexForDown;
            default:
                return null;
        }
    }
}
