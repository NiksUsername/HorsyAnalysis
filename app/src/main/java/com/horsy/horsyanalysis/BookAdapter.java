package com.horsy.horsyanalysis;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder>{
    private OnBookMoveClickListener onBookMoveClickListener;
    private OnBookMoveLongClickListener onBookMoveLongClickListener;

    private final ArrayList<BookMove> bookMoves;

    interface OnBookMoveClickListener {
        void onBookMoveClick(int position);
    }

    public void setOnBookMoveClickListener(OnBookMoveClickListener onBookMoveClickListener) {
        this.onBookMoveClickListener = onBookMoveClickListener;
    }

    interface OnBookMoveLongClickListener {
        boolean onBookMoveLongClick(int position);
    }

    public void setOnBookMoveLongClickListener(OnBookMoveLongClickListener onBookMoveLongClickListener) {
        this.onBookMoveLongClickListener = onBookMoveLongClickListener;
    }

    public BookAdapter(ArrayList<BookMove> bookMoves) {
        this.bookMoves = bookMoves;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.book_move, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        BookMove move = bookMoves.get(position);
        holder.move.setText(move.getMove());
        holder.moveCount.setText(String.valueOf(move.getCount()));
        holder.moveResults.setProgress(Math.round((float) move.getWins()/move.getCount()*100));
        holder.moveResults.setSecondaryProgress(Math.round((float) (move.getWins()+move.getDraws())/move.getCount()*100));
    }
    @Override
    public int getItemCount() {
        return bookMoves.size();
    }

    class BookViewHolder extends RecyclerView.ViewHolder{
        private final TextView move;
        private final TextView moveCount;
        private final ProgressBar moveResults;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            move = itemView.findViewById(R.id.move);
            moveCount = itemView.findViewById(R.id.moves_count);
            moveResults = itemView.findViewById(R.id.results);
            itemView.setOnClickListener(v -> {
                if (onBookMoveClickListener != null) {
                    onBookMoveClickListener.onBookMoveClick(getAdapterPosition());
                }
            });
            itemView.setOnLongClickListener(v -> {
                    if (onBookMoveLongClickListener != null) {
                        return onBookMoveLongClickListener.onBookMoveLongClick(getAdapterPosition());
                    }
                    return false;
                });
        }
    }
}
