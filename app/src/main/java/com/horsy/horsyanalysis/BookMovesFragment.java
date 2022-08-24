package com.horsy.horsyanalysis;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class BookMovesFragment extends Fragment {
    public static ArrayList<BookMove> bookMoves = new ArrayList<>();
    private BookAdapter bookAdapter;
    DatabaseHelper databaseHelper;
    SQLiteDatabase db;
    Cursor userCursor;

    String fen;

    Thread searchThread;
    boolean isSearching;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        databaseHelper = new DatabaseHelper(getContext());
        databaseHelper.create_db();

        View view = inflater.inflate(R.layout.fragment_book_moves, container, false);

        RecyclerView recyclerViewMoves = view.findViewById(R.id.book_move_list);

        bookAdapter = new BookAdapter(bookMoves);
        bookAdapter.setOnBookMoveClickListener(position -> {
            HorsyAnalysis activity = (HorsyAnalysis) getActivity();
            if (activity != null) {
                try {
                    activity.doStringMove(bookMoves.get(position).getMove());
                }catch (IndexOutOfBoundsException ignored) {}
            }
        });
        bookAdapter.setOnBookMoveLongClickListener(position -> {
            HorsyAnalysis activity = (HorsyAnalysis) getActivity();
            if (activity != null) {
                String move = bookMoves.get(position).getMove();
                int games = bookMoves.get(position).getCount();
                int wins = bookMoves.get(position).getWins();
                int draws = bookMoves.get(position).getDraws();
                activity.startBookMoveDialog(move,games,wins,draws);
                return true;
            }
            return false;
        });
        recyclerViewMoves.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewMoves.setAdapter(bookAdapter);

        setBookMoves(fen);

        return view;
    }

    public void setBookMoves(String fen) {
        this.fen = fen;
        stopMoveSearching();
        if (databaseHelper== null) return;
        if (searchThread != null) return;
        if (getActivity() == null) return;

        bookMoves.clear();

        Runnable run = () -> {
            isSearching = true;
            try {
                for (int i = 0; i < 20; i++) {
                    if (!isSearching) return;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                db = databaseHelper.open();
                userCursor = db.rawQuery(String.format("select * from %s where %s='%s' order by %s desc;", DatabaseHelper.TABLE, DatabaseHelper.COLUMN_FEN, fen,DatabaseHelper.COLUMN_GAMES), null);
                if (userCursor.moveToFirst()) {
                    int gamesId = userCursor.getColumnIndex(DatabaseHelper.COLUMN_GAMES);
                    int moveId = userCursor.getColumnIndex(DatabaseHelper.COLUMN_MOVE);
                    int winId = userCursor.getColumnIndex(DatabaseHelper.COLUMN_WIN);
                    int drawId = userCursor.getColumnIndex(DatabaseHelper.COLUMN_DRAW);
                    do {
                        bookMoves.add(new BookMove(userCursor.getString(moveId), userCursor.getInt(gamesId), userCursor.getInt(winId), userCursor.getInt(drawId)));
                    } while (userCursor.moveToNext());
                }
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> bookAdapter.notifyDataSetChanged());
                }
                Thread.sleep(50);

            }catch (Exception e) {
                e.printStackTrace();
            }finally {
                close();
                databaseHelper.close();
                searchThread = null;
            }
        };
        searchThread = new Thread(run);
        searchThread.start();
    }

    private void stopMoveSearching() {
        isSearching = false;
        try {
            searchThread.join();
        } catch (Exception ignored) {}
        searchThread = null;
    }

    public void close() {
        if (db != null) db.close();
        if (userCursor !=null) userCursor.close();
    }
}