/*
    CuckooChess - A java chess program.
    Copyright (C) 2011  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package guibase;

import chess.ChessParseError;
import chess.ComputerPlayer;
import chess.Game;
import chess.HumanPlayer;
import chess.Move;
import chess.MoveGen;
import chess.Piece;
import chess.Player;
import chess.Position;
import chess.Search;
import chess.TextIO;
import chess.UndoInfo;
import chess.Game.GameState;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** The glue between the chess engine and the GUI. */
public class ChessController {
    private Player humanPlayer;
    private ComputerPlayer computerPlayer;
    Game game;
    private final GUIInterface gui;
    private boolean humanIsWhite;
    private Thread computerThread;
    private boolean playWithComputer;
    private int threadStack;       // Thread stack size, or zero to use OS default

    // Search statistics
    private String thinkingPV;
    private String scorePv;

    class SearchListener implements Search.Listener {
        int currDepth = 0;
        String currMove = "";
        long currNodes = 0;
        int currNps = 0;
        int currTime = 0;

        int pvDepth = 0;
        int pvScore = 0;
        boolean pvIsMate = false;
        String pvStr = "";

        private void setSearchInfo() {
            if (pvIsMate) {
                scorePv=String.format(Locale.US, "m%d", pvScore);
            } else {
                scorePv=String.format(Locale.US, "%.2f", pvScore / 100.0);
            }
            gui.runOnUIThread(() -> {
                thinkingPV = pvStr;
                setThinkingPV();
            });
        }

        public void notifyDepth(int depth) {
            currDepth = depth;
            setSearchInfo();
        }

        public void notifyCurrMove(Move m,int moveNr) {
            currMove = TextIO.moveToString(new Position(game.pos), m, false);
            setSearchInfo();
        }

        public void notifyPV(int depth, int score, int time, long nodes, int nps, boolean isMate,
                              ArrayList<Move> pv) {
            pvDepth = depth;
            pvScore = game.pos.whiteMove ? score : -score;
            currTime = time;
            currNodes = nodes;
            currNps = nps;
            pvIsMate = isMate;

            StringBuilder buf = new StringBuilder();
            Position pos = new Position(game.pos);
            UndoInfo ui = new UndoInfo();
            for (Move m : pv) {
                buf.append(String.format(Locale.US, " %s", TextIO.moveToString(pos, m, false)));
                pos.makeMove(m, ui);
            }
            pvStr = buf.toString();
            setSearchInfo();
        }

        public void notifyStats(long nodes, int nps, int time) {
            currNodes = nodes;
            currNps = nps;
            currTime = time;
            setSearchInfo();

        }
    }
    private SearchListener listener;

    public ChessController(GUIInterface gui) {
        this.gui = gui;
        listener = new SearchListener();
        thinkingPV = "";
        threadStack = 0;
    }

    public void setThreadStackSize(int size) {
        threadStack = size;
    }

    public final void newGame(boolean humanIsWhite, int ttLogSize, boolean verbose, boolean playWithComputer) {
        stopComputerThinking();
        this.playWithComputer = playWithComputer;
        this.humanIsWhite = humanIsWhite;
        humanPlayer = new HumanPlayer();
        computerPlayer = new ComputerPlayer();
        computerPlayer.verbose = verbose;
        computerPlayer.useBook(false);
        computerPlayer.setTTLogSize(ttLogSize);
        computerPlayer.setListener(listener);
        if (humanIsWhite) {
            game = new Game(humanPlayer, computerPlayer);
        } else {
            game = new Game(computerPlayer, humanPlayer);
        }
    }
    public final void startGame() {
        gui.setSelection(-1);
        updateGUI();
        startComputerThinking();
    }

    public final void setPosHistory(List<String> posHistStr) {
        try {
            String fen = posHistStr.get(0);
            Position pos = TextIO.readFEN(fen);
            game.processString("new");
            game.pos = pos;
            for (String s : posHistStr.get(1).split(" ")) {
                game.processString(s);
            }
            int numUndo = Integer.parseInt(posHistStr.get(2));
            for (int i = 0; i < numUndo; i++) {
                game.processString("undo");
            }
        } catch (ChessParseError e) {
            // Just ignore invalid positions
        }
    }

    public final List<String> getPosHistory() {
        return game.getPosHistory();
    }

    public String getFEN() {
        return TextIO.toFEN(game.pos);
    }

    /** Convert current game to PGN format. */
    public String getPGN() {
        StringBuilder pgn = new StringBuilder();
        List<String> posHist = getPosHistory();
        String fen = posHist.get(0);
        String moves = game.getMoveListString(true);
        if (game.getGameState() == GameState.ALIVE)
            moves += " *";
        int year, month, day;
        {
            Calendar now = GregorianCalendar.getInstance();
            year = now.get(Calendar.YEAR);
            month = now.get(Calendar.MONTH) + 1;
            day = now.get(Calendar.DAY_OF_MONTH);
        }
        pgn.append(String.format(Locale.US, "[Date \"%04d.%02d.%02d\"]%n", year, month, day));
        String white = "Player";
        String black = "Player";
        if (playWithComputer && humanIsWhite) {
            black = ComputerPlayer.engineName;
        }else if (playWithComputer) {
            white = ComputerPlayer.engineName;
        }
        pgn.append(String.format(Locale.US, "[White \"%s\"]%n", white));
        pgn.append(String.format(Locale.US, "[Black \"%s\"]%n", black));
        pgn.append(String.format(Locale.US, "[Result \"%s\"]%n", game.getPGNResultString()));
        if (!fen.equals(TextIO.startPosFEN)) {
            pgn.append(String.format(Locale.US, "[FEN \"%s\"]%n", fen));
            pgn.append("[SetUp \"1\"]\n");
        }
        pgn.append("\n");
        int currLineLength = 0;
        for (String s : moves.split(" ")) {
            String move = s.trim();
            int moveLen = move.length();
            if (moveLen > 0) {
                if (currLineLength + 1 + moveLen >= 80) {
                    pgn.append("\n");
                    pgn.append(move);
                    currLineLength = moveLen;
                } else {
                    if (currLineLength > 0) {
                        pgn.append(" ");
                        currLineLength++;
                    }
                    pgn.append(move);
                    currLineLength += moveLen;
                }
            }
        }
        pgn.append("\n\n");
        return pgn.toString();
    }

    public void setPGN(String pgn) throws ChessParseError {
        // First pass, remove comments
        {
            StringBuilder out = new StringBuilder();
            Scanner sc = new Scanner(pgn);
            sc.useDelimiter("");
            while (sc.hasNext()) {
                String c = sc.next();
                if (c.equals("{")) {
                    sc.skip("[^}]*}");
                } else if (c.equals(";")) {
                    sc.skip("[^\n]*\n");
                } else {
                    out.append(c);
                }
            }
            pgn = out.toString();
            sc.close();
        }

        // Parse tag section
        Position pos = TextIO.readFEN(TextIO.startPosFEN);
        Scanner sc = new Scanner(pgn);
        sc.useDelimiter("\\s+");
        while (sc.hasNext("\\[.*")) {
            String tagName = sc.next();
            if (tagName.length() > 1) {
                tagName = tagName.substring(1);
            } else {
                tagName = sc.next();
            }
            String tagValue = sc.findWithinHorizon(".*\\]", 0);
            tagValue = tagValue.trim();
            if (tagValue.charAt(0) == '"')
                tagValue = tagValue.substring(1);
            if (tagValue.charAt(tagValue.length()-1) == ']')
                tagValue = tagValue.substring(0, tagValue.length() - 1);
            if (tagValue.charAt(tagValue.length()-1) == '"')
                tagValue = tagValue.substring(0, tagValue.length() - 1);
            if (tagName.equals("FEN")) {
                pos = TextIO.readFEN(tagValue);
            }
        }
        game.processString("new");
        game.pos = pos;

        // Handle (ignore) recursive annotation variations
        {
            StringBuilder out = new StringBuilder();
            sc.useDelimiter("");
            int level = 0;
            while (sc.hasNext()) {
                String c = sc.next();
                if (c.equals("(")) {
                    level++;
                } else if (c.equals(")")) {
                    level--;
                } else if (level == 0) {
                    out.append(c);
                }
            }
            pgn = out.toString();
        }

        // Parse move text section
        sc.close();
        sc = new Scanner(pgn);
        sc.useDelimiter("\\s+");
        while (sc.hasNext()) {
            String strMove = sc.next();
            strMove = strMove.replaceFirst("\\$?[0-9]*\\.*([^?!]*)[?!]*", "$1");
            if (strMove.length() == 0) continue;
            Move m = TextIO.stringToMove(game.pos, strMove);
            if (m == null)
                break;
            game.processString(strMove);
        }
        sc.close();
    }

    public void setFENOrPGN(String fenPgn) throws ChessParseError {
        try {
            Position pos = TextIO.readFEN(fenPgn);
            game.processString("new");
            game.pos = pos;
        } catch (ChessParseError e) {
            // Try read as PGN instead
            setPGN(fenPgn);
        }
        gui.setSelection(-1);
        updateGUI();
        startComputerThinking();
    }

    /** Set color for human player. Doesn't work when computer is thinking. */
    public final void setHumanWhite(final boolean humanIsWhite) {
        if (this.humanIsWhite != humanIsWhite) {
            this.humanIsWhite = humanIsWhite;
            game.processString("swap");
        }
        startComputerThinking();
    }

    public final boolean humansTurn() {
        return game.pos.whiteMove == humanIsWhite;
    }

    public final boolean isWhiteMove() {
        return game.pos.whiteMove;
    }
    public final boolean computerThinking() {
        return computerThread != null;
    }

    public final void takeBackMove() {
        if (game.getLastMove() != null) {
            gui.onMove();
            game.processString("undo");
            updateGUI();
            setSelection();
            startComputerThinking();
        }
    }

    public final void redoMove() {
        if (game.isRedoable()) {
            gui.onMove();
            game.processString("redo");
            updateGUI();
            setSelection();
            startComputerThinking();
        }
    }

    public final void humanMove(Move m) {
        if (doMove(m)) {
            updateGUI();
            startComputerThinking();
            gui.onMove();
        }
    }

    public final Move getMove(String str) {
        return TextIO.stringToMove(game.pos,str);
    }

    private Move promoteMove;
    public final void reportPromotePiece(int choice) {
        final boolean white = game.pos.whiteMove;
        int promoteTo;
        switch (choice) {
            case 1:
                promoteTo = white ? Piece.WROOK : Piece.BROOK;
                break;
            case 2:
                promoteTo = white ? Piece.WBISHOP : Piece.BBISHOP;
                break;
            case 3:
                promoteTo = white ? Piece.WKNIGHT : Piece.BKNIGHT;
                break;
            default:
                promoteTo = white ? Piece.WQUEEN : Piece.BQUEEN;
                break;
        }
        promoteMove.promoteTo = promoteTo;
        Move m = promoteMove;
        promoteMove = null;
        humanMove(m);
    }

    /**
     * Move a piece from one square to another.
     * @return True if the move was legal, false otherwise.
     */
    private boolean doMove(Move move) {

        Position pos = game.pos;
        MoveGen.MoveList moves = new MoveGen().pseudoLegalMoves(pos);
        MoveGen.removeIllegal(pos, moves);
        int promoteTo = move.promoteTo;
        for (int mi = 0; mi < moves.size; mi++) {
            Move m = moves.m[mi];
            if ((m.from == move.from) && (m.to == move.to)) {
                if ((m.promoteTo != Piece.EMPTY) && (promoteTo == Piece.EMPTY)) {
                    promoteMove = m;
                    gui.requestPromotePiece();
                    return false;
                }
                if (m.promoteTo == promoteTo) {
                    String strMove = TextIO.moveToString(pos, m, false);
                    game.processString(strMove);
                    return true;
                }
            }
        }
        gui.reportInvalidMove(move);
        return false;
    }


    private void updateGUI() {
        setStatusString();
        setMoveList();
        setThinkingPV();
        gui.setPosition(game.pos);
    }

    private void setStatusString() {
        gui.setStatusString(game.getGameState());
    }

    public final void setMoveList() {
        String str = game.getMoveListString(false);
        gui.setMoveListString(str);
    }

    private void setThinkingPV() {
        String pv = "";
        String score = "";
        if (gui.showThinking()) {
            pv = thinkingPV;
            score = scorePv;
        }
        gui.setThinkingString(pv,score);
    }

    private void setSelection() {
        Move m = game.getLastMove();
        int sq = (m != null) ? m.to : -1;
        gui.setSelection(sq);
    }

    public void setPlayWithComputer(boolean playWithComputer) {
        this.playWithComputer = playWithComputer;
    }

    public void startComputerThinking() {
        stopComputerThinking();
        if (computerThread == null) {
            Runnable run = () -> {
                try {
                    computerPlayer.timeLimit(gui.timeLimit(), gui.timeLimit(), gui.randomMode());
                    for (int i=0;i<90;i++){
                        if (computerPlayer.getMaxTimeMillis()==0) {
                            return;}
                        Thread.sleep(10);
                    }
                    final String cmd = computerPlayer.getCommand(new Position(game.pos),
                            game.haveDrawOffer(), game.getHistory());
                    gui.runOnUIThread(() -> {
                        if (playWithComputer ? !humansTurn() : false) {
                            game.processString(cmd);
                            gui.onMove();
                        }
                        updateGUI();
                        setSelection();
                });
                    Thread.sleep(10);
                } catch (Exception e) {
                e.printStackTrace();
            }
            };
            if (threadStack > 0) {
                ThreadGroup tg = new ThreadGroup("searcher");
                computerThread = new Thread(tg, run, "searcher", threadStack);
            } else {
                computerThread = new Thread(run);
            }
            thinkingPV = "";
            updateGUI();
            computerThread.start();
        }
    }

    public synchronized void stopComputerThinking() {
        if (computerThread != null) {
            computerPlayer.timeLimit(0, 0, false);
            try {
                computerThread.join();
            } catch (InterruptedException ex) {
                System.out.printf("Could not stop thread%n");
            }
            computerThread = null;
            updateGUI();
        }
    }

    public synchronized void setTimeLimit() {
        if (computerThread != null) {
            computerPlayer.timeLimit(gui.timeLimit(), gui.timeLimit(), gui.randomMode());
        }
    }
}
