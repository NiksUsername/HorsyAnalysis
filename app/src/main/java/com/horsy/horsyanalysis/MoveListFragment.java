package com.horsy.horsyanalysis;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

public class MoveListFragment extends Fragment {

    private TextView moves;
    private ScrollView scrollView;

    private String strMoves;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_move_list, container, false);

        moves = view.findViewById(R.id.moveList);
        scrollView = view.findViewById(R.id.moveListScroll);
        setText(strMoves);
        return view;
    }

    public void setText(String text) {
        strMoves = text;
        if (moves!=null){
            moves.setText(text);
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        }
    }
}