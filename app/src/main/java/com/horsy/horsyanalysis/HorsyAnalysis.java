package com.horsy.horsyanalysis;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.List;

import chess.ChessParseError;
import chess.Game;
import chess.Move;
import chess.Position;
import chess.TextIO;
import guibase.ChessController;
import guibase.GUIInterface;

public class HorsyAnalysis extends AppCompatActivity implements GUIInterface {
    ChessController ctrl;
    boolean mShowThinking;
    int mTimeLimit;
    boolean playerWhite;
    boolean playWithComputer;
    boolean isSoundOn;
    static final int ttLogSize = 16; // Use 2^ttLogSize hash entries.

    SharedPreferences settings;
    BookMovesFragment bookMovesFragment;
    MoveListFragment moveListFragment;

    ChessBoard chessboard;
    TextView thinking,status,score;

    MediaPlayer mediaPlayer;

    private void readPrefs() {
        mShowThinking = settings.getBoolean("showThinking", true);
        thinking.setVisibility(mShowThinking ? View.VISIBLE : View.GONE);
        score.setVisibility(mShowThinking ? View.VISIBLE : View.GONE);
        playWithComputer = settings.getBoolean("playWithComputer",false);
        ctrl.setPlayWithComputer(playWithComputer);
        mTimeLimit = settings.getInt("timeLimit", 500000);
        isSoundOn = settings.getBoolean("isSoundOn", true);
        boolean boardFlipped = settings.getBoolean("boardFlipped", false);
        playerWhite = !boardFlipped;
        chessboard.setFlipped(boardFlipped);
        ctrl.setTimeLimit();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            readPrefs();
            if (!ctrl.computerThinking()) ctrl.stopComputerThinking();
        });

        status = findViewById(R.id.status);
        thinking= findViewById(R.id.thinking);
        chessboard = findViewById(R.id.chessBoard);
        score = findViewById(R.id.score);
        status.setFocusable(false);
        moveListFragment = new MoveListFragment();
        bookMovesFragment = new BookMovesFragment();

        findViewById(R.id.radioButtonBook).setOnClickListener(radioButtonClickListener);
        findViewById(R.id.radioButtonMoves).setOnClickListener(radioButtonClickListener);
        thinking.setFocusable(false);
        ctrl = new ChessController(this);
        ctrl.setThreadStackSize(0);
        readPrefs();

        BottomNavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setOnItemSelectedListener(navigationListener);

        Typeface chessFont = Typeface.createFromAsset(getAssets(), "chessfont.ttf");
        chessboard.setFont(chessFont);
        chessboard.setFocusable(true);
        chessboard.requestFocus();
        chessboard.setClickable(true);

        ctrl.newGame(playerWhite, ttLogSize, false, playWithComputer);
        {
            String fen = "";
            String moves = "";
            String numUndo = "0";
            String tmp;
            if (savedInstanceState != null) {
                tmp = savedInstanceState.getString("startFEN");
                if (tmp != null) fen = tmp;
                tmp = savedInstanceState.getString("moves");
                if (tmp != null) moves = tmp;
                tmp = savedInstanceState.getString("numUndo");
                if (tmp != null) numUndo = tmp;
            } else {
                tmp = settings.getString("startFEN", null);
                if (tmp != null) fen = tmp;
                tmp = settings.getString("moves", null);
                if (tmp != null) moves = tmp;
                tmp = settings.getString("numUndo", null);
                if (tmp != null) numUndo = tmp;
            }
            List<String> posHistStr = new ArrayList<>();
            posHistStr.add(fen);
            posHistStr.add(moves);
            posHistStr.add(numUndo);
            ctrl.setPosHistory(posHistStr);
            setMoveListString(moves);
        }
        ctrl.startGame();

        getSupportFragmentManager().beginTransaction().replace(R.id.container,bookMovesFragment).commit();
        String[] fen = ctrl.getFEN().split(" ");
        bookMovesFragment.setBookMoves(fen[0]+" "+fen[1]+" "+fen[2]);

        getSupportFragmentManager().beginTransaction().replace(R.id.container,moveListFragment).commit();

        chessboard.setOnTouchListener((v, event) -> {
            if ((event.getAction() == MotionEvent.ACTION_UP) && (playWithComputer ? ctrl.humansTurn() : true)) {
                int sq = chessboard.eventToSquare(event);
                Move m = chessboard.mousePressed(sq);
                if (m != null) {
                    ctrl.humanMove(m);
                }
                return false;
            }
            return false;
        });
        chessboard.setOnLongClickListener(v -> {
            startClipDataDialog();
            return true;
        });
    }

    private final NavigationBarView.OnItemSelectedListener navigationListener = new NavigationBarView.OnItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_menu:
                    startMenuDialog();
                    return true;
                case R.id.navigation_flip:
                    chessboard.setFlipped(!chessboard.isFlipped());
                    ctrl.setHumanWhite(!chessboard.isFlipped());
                    return true;
                case R.id.navigation_previous:
                    ctrl.takeBackMove();
                    return true;
                case R.id.navigation_next: {
                    ctrl.redoMove();
                    return true;
                }
            }
            return false;
        }
    };

    private final View.OnClickListener radioButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            RadioButton rb = (RadioButton)v;
            switch (rb.getId()) {
                case R.id.radioButtonBook:
                    getSupportFragmentManager().beginTransaction().replace(R.id.container,bookMovesFragment).commit();
                    String[] fen = ctrl.getFEN().split(" ");
                    bookMovesFragment.setBookMoves(fen[0]+" "+fen[1]+" "+fen[2]);
                    break;
                case R.id.radioButtonMoves:
                    getSupportFragmentManager().beginTransaction().replace(R.id.container,moveListFragment).commit();
            }
        }
    };

    public void doStringMove(String str) {
        if (playWithComputer ? ctrl.humansTurn() : true) ctrl.humanMove(ctrl.getMove(str));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        List<String> posHistStr = ctrl.getPosHistory();
        outState.putString("startFEN", posHistStr.get(0));
        outState.putString("moves", posHistStr.get(1));
        outState.putString("numUndo", posHistStr.get(2));
        outState.putBoolean("showThinking", showThinking());
        outState.putBoolean("boardFlipped", chessboard.isFlipped());
        outState.putBoolean("playWithComputer", playWithComputer);
        outState.putInt("timeLimit", mTimeLimit);
        outState.putBoolean("isSoundOn", isSoundOn);
    }

    @Override
    protected void onPause() {
        List<String> posHistStr = ctrl.getPosHistory();
        Editor editor = settings.edit();
        editor.putString("startFEN", posHistStr.get(0));
        editor.putString("moves", posHistStr.get(1));
        editor.putString("numUndo", posHistStr.get(2));
        editor.putBoolean("showThinking", showThinking());
        editor.putBoolean("boardFlipped", chessboard.isFlipped());
        editor.putBoolean("playWithComputer", playWithComputer);
        editor.putInt("timeLimit", mTimeLimit);
        editor.putBoolean("isSoundOn", isSoundOn);
        editor.apply();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ctrl.stopComputerThinking();
        bookMovesFragment.close();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {
            readPrefs();
            if (!ctrl.computerThinking()) ctrl.stopComputerThinking();
        }
    }

    @Override
    public void setPosition(Position pos) {
        chessboard.setPosition(pos);
        if (!ctrl.computerThinking()) ctrl.stopComputerThinking();
    }

    @Override
    public void setSelection(int sq) {
        chessboard.setSelection(sq);
    }

    @Override
    public void setStatusString(Game.GameState gameState) {
        String str;
        switch (gameState) {
            case ALIVE:
                str = ctrl.isWhiteMove() ? getString(R.string.game_state_WHITE) : getString(R.string.game_state_BLACK);
                break;
            case WHITE_MATE:
                str = getString(R.string.game_state_WHITE_MATE);
                break;
            case BLACK_MATE:
                str = getString(R.string.game_state_BLACK_MATE);
                break;
            case WHITE_STALEMATE:
            case BLACK_STALEMATE:
                str = getString(R.string.game_state_BLACK_STALEMATE);
                break;
            case DRAW_REP:
                str = getString(R.string.game_state_DRAW_REP);
                break;
            case DRAW_50:
                str = getString(R.string.game_state_DRAW_50);
                break;
            case DRAW_NO_MATE:
                str = getString(R.string.game_state_DRAW_NO_MATE);
                break;
            case DRAW_AGREE:
                str = getString(R.string.game_state_DRAW_AGREE);
                break;
            case RESIGN_WHITE:
                str = getString(R.string.game_state_RESIGN_WHITE);
                break;
            case RESIGN_BLACK:
                str = getString(R.string.game_state_RESIGN_BLACK);
                break;
            default:
                throw new RuntimeException();
        }
        status.setText(str);
    }

    @Override
    public void setMoveListString(String str) {
        moveListFragment.setText(str);
        String[] fen = ctrl.getFEN().split(" ");
        bookMovesFragment.setBookMoves(fen[0]+" "+fen[1]+" "+fen[2]);
    }

    @Override
    public void setThinkingString(String pv, String score) {
        if (score==null) {
            return;
        }
        if(score.contains("-")) {
            this.score.setTextColor(getColor(R.color.white));
            this.score.setBackground(AppCompatResources.getDrawable(getApplicationContext(),R.drawable.small_corners_black));
        }else {
            this.score.setTextColor(getColor(R.color.black));
            this.score.setBackground(AppCompatResources.getDrawable(getApplicationContext(),R.drawable.small_corners_white));
        }
        this.score.setText(score);
        thinking.setText(pv);
    }

    @Override
    public int timeLimit() {
        return mTimeLimit;
    }

    @Override
    public boolean randomMode() {
        return mTimeLimit == -1;
    }

    @Override
    public boolean showThinking() {
        return mShowThinking;
    }

    public void startPromotionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.promotion_dialog);
        dialog.setCanceledOnTouchOutside(true);

        TextView queen = dialog.findViewById(R.id.promote_to_queen);
        TextView rook = dialog.findViewById(R.id.promote_to_rook);
        TextView bishop = dialog.findViewById(R.id.promote_to_bishop);
        TextView knight = dialog.findViewById(R.id.promote_to_knight);

        int color = ctrl.isWhiteMove() ? getColor(R.color.white) : getColor(R.color.black);
        queen.setTextColor(color);
        rook.setTextColor(color);
        bishop.setTextColor(color);
        knight.setTextColor(color);

        queen.setOnClickListener(View -> {
            ctrl.reportPromotePiece(0);
            dialog.cancel();});
        rook.setOnClickListener(View -> {
            ctrl.reportPromotePiece(1);
            dialog.cancel();});
        bishop.setOnClickListener(View -> {
            ctrl.reportPromotePiece(2);
            dialog.cancel();});
        knight.setOnClickListener(View -> {
            ctrl.reportPromotePiece(3);
            dialog.cancel();});

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    public void startClipDataDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.clipboard_dialog);
        dialog.setCanceledOnTouchOutside(true);

        TextView copyGame = dialog.findViewById(R.id.button_copy_game);
        TextView copyPosition = dialog.findViewById(R.id.button_copy_pos);
        TextView paste = dialog.findViewById(R.id.button_paste);

        copyGame.setOnClickListener(View ->{
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("", ctrl.getPGN());
            clipboard.setPrimaryClip(clip);
            dialog.cancel();
        });
        copyPosition.setOnClickListener(View ->{
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("", ctrl.getFEN());
            clipboard.setPrimaryClip(clip);
            dialog.cancel();
        });
        paste.setOnClickListener(View ->{
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            try {
                ctrl.setFENOrPGN(clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            } catch (ChessParseError | NullPointerException ignore ) {}
            finally {
                dialog.cancel();
            }
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void startMenuDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.menu_dialog);
        dialog.setCanceledOnTouchOutside(true);

        Switch showThinking = dialog.findViewById(R.id.show_thinking);
        showThinking.setChecked(mShowThinking);
        Switch playWithComputer = dialog.findViewById(R.id.play_with_computer);
        playWithComputer.setChecked(this.playWithComputer);
        Switch soundSwitch = dialog.findViewById(R.id.sound_switch);
        soundSwitch.setChecked(isSoundOn);
        TextView newGame = dialog.findViewById(R.id.new_game);
        TextView shareGame = dialog.findViewById(R.id.share_game);

        showThinking.setOnCheckedChangeListener((CompoundButton btn, boolean checked) -> {
            mShowThinking = checked;
            int visibility = checked ? View.VISIBLE : View.GONE;
            thinking.setVisibility(visibility);
            score.setVisibility(visibility);
        });

        playWithComputer.setOnCheckedChangeListener((CompoundButton btn, boolean checked) -> {
            this.playWithComputer = checked;
            ctrl.setPlayWithComputer(this.playWithComputer);
            mTimeLimit = checked ? 500 : 500000;
            playerWhite = !chessboard.isFlipped();
            ctrl.setHumanWhite(playerWhite);
        });

        soundSwitch.setOnCheckedChangeListener((CompoundButton btn, boolean checked) -> isSoundOn = checked);

        newGame.setOnClickListener(View -> {
            ctrl.newGame(playerWhite, ttLogSize, false, this.playWithComputer);
            ctrl.startGame();
            dialog.cancel();
        });

        shareGame.setOnClickListener(View -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra("Share game", ctrl.getPGN());
            startActivity(Intent.createChooser(intent,"Share using..."));
            dialog.cancel();
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void requestPromotePiece() {
        runOnUIThread(this::startPromotionDialog);
    }

    @Override
    public void reportInvalidMove(Move m) {
        String msg = String.format("Invalid move %s-%s", TextIO.squareToString(m.from), TextIO.squareToString(m.to));
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMove() {
        if (!isSoundOn) return;
        new Thread(() -> {
            mediaPlayer = MediaPlayer.create(HorsyAnalysis.this, R.raw.move);
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(MediaPlayer::release);
        }).start();
    }

    @Override
    public void runOnUIThread(Runnable runnable) {
        runOnUiThread(runnable);
    }
}
