package com.horsy.horsyanalysis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import chess.Move;
import chess.Piece;
import chess.Position;

public class ChessBoard extends View {
    private Position pos;
    private int selectedSquare;
    private final float cursorX;
    private final float cursorY;
    private boolean cursorVisible;
    private int x0, y0, sqSize;
    private boolean flipped;

    private final Paint darkPaint;
    private final Paint brightPaint;
    private final Paint redOutline;
    private final Paint greenOutline;
    private final Paint whitePiecePaint;
    private final Paint blackPiecePaint;

    public ChessBoard(Context context, AttributeSet attrs) {
        super(context, attrs);
        pos = new Position();
        selectedSquare = -1;
        cursorX = cursorY = 0;
        cursorVisible = false;
        x0 = y0 = sqSize = 0;
        flipped = false;

        darkPaint = new Paint();
        darkPaint.setARGB(255, 181, 136, 99);

        brightPaint = new Paint();
        brightPaint.setARGB(255, 236, 212, 176);

        redOutline = new Paint();
        redOutline.setARGB(255, 255, 0, 0);
        redOutline.setStyle(Paint.Style.STROKE);
        redOutline.setAntiAlias(true);

        greenOutline = new Paint();
        greenOutline.setARGB(255, 0, 255, 0);
        greenOutline.setStyle(Paint.Style.STROKE);
        greenOutline.setAntiAlias(true);

        whitePiecePaint = new Paint();
        whitePiecePaint.setARGB(255, 255, 255, 255);
        whitePiecePaint.setAntiAlias(true);

        blackPiecePaint = new Paint();
        blackPiecePaint.setARGB(255, 0, 0, 0);
        blackPiecePaint.setAntiAlias(true);
    }

    public void setFont(Typeface tf) {
        whitePiecePaint.setTypeface(tf);
        blackPiecePaint.setTypeface(tf);
        invalidate();
    }

    /**
     * Set the board to a given state.
     */
    final public void setPosition(Position pos) {
        this.pos = pos;
        invalidate();
    }

    public boolean isFlipped() {
        return flipped;
    }

    /**
     * Set/clear the board flipped status.
     */
    final public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        invalidate();
    }

    /**
     * Set/clear the selected square.
     * @param square The square to select, or -1 to clear selection.
     */
    final public void setSelection(int square) {
        if (square != selectedSquare) {
            selectedSquare = square;
            invalidate() ;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        int minSize = Math.min(width, height);
        setMeasuredDimension(minSize, minSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int width = getWidth();
        final int height = getHeight();
        sqSize = (Math.min(width, height) - 4) / 8;
        x0 = (width - sqSize * 8) / 2;
        y0 = (height - sqSize * 8) / 2;

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                final int xCrd = getXCrd(x);
                final int yCrd = getYCrd(y);
                Paint paint = Position.darkSquare(x, y) ? darkPaint : brightPaint;
                canvas.drawRect(xCrd, yCrd, xCrd+sqSize, yCrd+sqSize, paint);

                int sq = Position.getSquare(x, y);
                int p = pos.getPiece(sq);
                drawPiece(canvas, xCrd + sqSize / 2, yCrd + sqSize / 2, p);
            }
        }
        if (selectedSquare >= 0) {
            int selX = Position.getX(selectedSquare);
            int selY = Position.getY(selectedSquare);
            redOutline.setStrokeWidth(sqSize/(float)16);
            int x0 = getXCrd(selX);
            int y0 = getYCrd(selY);
            canvas.drawRect(x0, y0, x0 + sqSize, y0 + sqSize, redOutline);
        }
        if (cursorVisible) {
            int x = Math.round(cursorX);
            int y = Math.round(cursorY);
            int x0 = getXCrd(x);
            int y0 = getYCrd(y);
            greenOutline.setStrokeWidth(sqSize/(float)16);
            canvas.drawRect(x0, y0, x0 + sqSize, y0 + sqSize, greenOutline);
        }
    }

    private void drawPiece(Canvas canvas, int xCrd, int yCrd, int p) {
        String ps;
        switch (p) {
            case Piece.EMPTY:
                ps = "";
                break;
            case Piece.WKING:
                ps = "k";
                break;
            case Piece.WQUEEN:
                ps = "q";
                break;
            case Piece.WROOK:
                ps = "r";
                break;
            case Piece.WBISHOP:
                ps = "b";
                break;
            case Piece.WKNIGHT:
                ps = "n";
                break;
            case Piece.WPAWN:
                ps = "p";
                break;
            case Piece.BKING:
                ps = "l";
                break;
            case Piece.BQUEEN:
                ps = "w";
                break;
            case Piece.BROOK:
                ps = "t";
                break;
            case Piece.BBISHOP:
                ps = "v";
                break;
            case Piece.BKNIGHT:
                ps = "m";
                break;
            case Piece.BPAWN:
                ps = "o";
                break;
            default:
                ps = "?";
                break;
        }
        if (ps.length() > 0) {
            Paint paint = Piece.isWhite(p) ? whitePiecePaint : blackPiecePaint;
            paint.setTextSize(sqSize);
            Rect bounds = new Rect();
            paint.getTextBounds(ps, 0, ps.length(), bounds);
            int xCent = bounds.centerX();
            int yCent = bounds.centerY();
            canvas.drawText(ps, xCrd - xCent, yCrd - yCent, paint);
        }
    }

    private int getXCrd(int x) {
        return x0 + sqSize * (flipped ? 7 - x : x);
    }
    private int getYCrd(int y) {
        return y0 + sqSize * (flipped ? y : (7 - y));
    }

    /**
     * Compute the square corresponding to the coordinates of a mouse event.
     * @param evt Details about the mouse event.
     * @return The square corresponding to the mouse event, or -1 if outside board.
     */
    final int eventToSquare(MotionEvent evt) {
        int xCrd = (int)(evt.getX());
        int yCrd = (int)(evt.getY());

        int sq = -1;
        if ((xCrd >= x0) && (yCrd >= y0) && (sqSize > 0)) {
            int x = (xCrd - x0) / sqSize;
            int y = 7 - (yCrd - y0) / sqSize;
            if ((x >= 0) && (x < 8) && (y >= 0) && (y < 8)) {
                if (flipped) {
                    x = 7 - x;
                    y = 7 - y;
                }
                sq = Position.getSquare(x, y);
            }
        }
        return sq;
    }

    final Move mousePressed(int sq) {
        if (sq < 0)
            return null;
        cursorVisible = false;
        if (selectedSquare >= 0) {
            int p = pos.getPiece(selectedSquare);
            if ((p == Piece.EMPTY) || (Piece.isWhite(p) != pos.whiteMove)) {
                setSelection(-1); // Remove selection of opponents last moving piece
            }
        }

        int p = pos.getPiece(sq);
        if (selectedSquare >= 0) {
            if (sq != selectedSquare) {
                if ((p == Piece.EMPTY) || (Piece.isWhite(p) != pos.whiteMove)) {
                    Move m = new Move(selectedSquare, sq, Piece.EMPTY);
                    setSelection(sq);
                    return m;
                }
            }
            setSelection(-1);
        } else {
            boolean myColor = (p != Piece.EMPTY) && (Piece.isWhite(p) == pos.whiteMove);
            if (myColor) {
                setSelection(sq);
            }
        }
        return null;
    }
}
