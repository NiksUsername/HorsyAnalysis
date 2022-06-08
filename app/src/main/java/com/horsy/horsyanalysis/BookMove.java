package com.horsy.horsyanalysis;

public class BookMove {
    private final String move;
    private final int count;
    private final int wins;
    private final int draws;

    public BookMove(String move, int count, int wins, int draws) {
        this.move = move;
        this.count = count;
        this.wins = wins;
        this.draws = draws;
    }

    public String getMove() {
        return move;
    }

    public int getCount() {
        return count;
    }

    public int getWins() {
        return wins;
    }

    public int getDraws() {
        return draws;
    }
}
