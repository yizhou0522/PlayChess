package com.example.playChess;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;

import com.example.playChess.Pieces.Bishop;
import com.example.playChess.Pieces.King;
import com.example.playChess.Pieces.Knight;
import com.example.playChess.Pieces.Pawn;
import com.example.playChess.Pieces.Piece;
import com.example.playChess.Pieces.Queen;
import com.example.playChess.Pieces.Rook;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


class Game {
    byte state;
    private byte previousState;
    List<Position> movePointers;
    List<Position> attackPointers;
    private Piece activePiece;
    byte activeColor;
    List<Piece> pieces;
    private List<Piece> capturedPieces; // added for future extensions
    private Piece whiteKing, blackKing;
    private List<Piece> enPassantInPast;

    private final Context context;
    private final GameActivity gameActivity;

    Game(Context c, GameActivity gA){
        context = c;
        gameActivity = gA;
    }

    void start(){
        state = Const.STATE_SELECT;
        previousState = state;
        pieces = new ArrayList<>();
        capturedPieces = new ArrayList<>();
        movePointers = new ArrayList<>();
        attackPointers = new ArrayList<>();
        enPassantInPast = new ArrayList<>();

        activeColor = Const.WHITE;

        for (byte color = Const.WHITE; color <= Const.BLACK; color++){
            for(int i = 0; i < 8; i++) pieces.add(new Pawn(context, color));
            pieces.add(new Bishop(context, color));
            pieces.add(new Bishop(context, color));
            pieces.add(new Knight(context, color));
            pieces.add(new Knight(context, color));
            pieces.add(new Rook(context, color));
            pieces.add(new Rook(context, color));
            pieces.add(new Queen(context, color));
            pieces.add(new King(context, color));
            if(color == Const.WHITE) whiteKing = pieces.get(pieces.size()-1);
            else blackKing = pieces.get(pieces.size()-1);
        }

        gameActivity.changeTurn(activeColor);

    }

    void end(byte w){
        state = Const.STATE_END;
        gameActivity.endOfTheGame(w);
    }

    private void changeTurn(){
        for(Piece i : pieces) if(i.enPassant){
            if(enPassantInPast.contains(i)){
                i.enPassant = false;
                enPassantInPast.remove(i);
            }
            else enPassantInPast.add(i);
        }

        Piece king;
        // change to BLACK
        if(activeColor == Const.WHITE){
            activeColor = Const.BLACK;
            king = blackKing;
        }
        // change to WHITE
        else{
            activeColor = Const.WHITE;
            king = whiteKing;
        }

        gameActivity.changeTurn(activeColor);

        if(!mayCheckBeAvoided(king)) {
            if (activeColor == Const.WHITE) end(Const.BLACK);
            else end(Const.WHITE);
        }
        else if(isSquareAttacked(king.position, king)){
            gameActivity.vibrate(200);
            GameManagement.makeToast(R.string.toast_check, GameManagement.switchColor(activeColor), gameActivity);
        }
    }

    void processTouch(MotionEvent event, Position touchPosition){
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                switch (state) {
                    case Const.STATE_SELECT:
                        for (Piece i : pieces) if(i.color == activeColor)
                            if (Position.areEqual(i.position, touchPosition)) {
                                activePiece = i;
                                movePointers = getMovePointers(activePiece);
                                attackPointers = getAttackPointers(activePiece);
                                if(activePiece instanceof King){
                                    movePointers = removeAttacked(movePointers, activePiece);
                                    attackPointers = removeAttacked(attackPointers, activePiece);
                                }
                                else{
                                    movePointers = makeKingSafe(activePiece, movePointers);
                                    attackPointers = makeKingSafe(activePiece, attackPointers);
                                }
                                state = Const.STATE_MOVE_ATTACK;
                                break;
                            }
                        break;

                    case Const.STATE_MOVE_ATTACK:
                        state = Const.STATE_SELECT; // here because of possible change to STATE_END
                        if (Position.areEqual(touchPosition, activePiece.position)) break;
                        for (Position i : movePointers)
                            if (Position.areEqual(i, touchPosition)) {
                                if(activePiece instanceof King)
                                    if(Math.abs(activePiece.position.x - touchPosition.x) > 1){
                                        getCloserRook(touchPosition.x, activePiece.color).moveTo(
                                                new Position((activePiece.position.x + touchPosition.x)/2, touchPosition.y));
                                }
                                activePiece.moveTo(touchPosition);
                                if(activePiece instanceof Pawn){ // check promotion possibility
                                    if(activePiece.color == Const.WHITE
                                            && activePiece.position.y == 7) promotion(activePiece);
                                    else if(activePiece.position.y == 0) promotion(activePiece);
                                }
                                changeTurn();
                                break;
                            }
                        for (Position i : attackPointers)
                            if (Position.areEqual(i, touchPosition)){
                                if(activePiece instanceof Pawn) if(!pieceOnSquare(i)){
                                    if(activePiece.color == Const.WHITE)
                                        capture(getPieceOn(new Position(touchPosition.x, touchPosition.y - 1)));
                                    else
                                        capture(getPieceOn(new Position(touchPosition.x, touchPosition.y + 1)));
                                }
                                if(pieceOnSquare(touchPosition)) capture(getPieceOn(touchPosition));

                                activePiece.moveTo(touchPosition);
                                if(activePiece instanceof Pawn){ // check promotion possibility
                                    if(activePiece.color == Const.WHITE
                                            && activePiece.position.y == 7) promotion(activePiece);
                                    else if(activePiece.position.y == 0) promotion(activePiece);
                                }
                                changeTurn();
                                break;
                            }


                        movePointers = new ArrayList<>();
                        attackPointers = new ArrayList<>();
                        break;

                    case Const.STATE_END:
                    case Const.STATE_PAUSE:
                        break;
                }
        }
    }

    private boolean pieceOnSquare(Position square){
        for(Piece p : pieces) if(p.position != null) // may be null because of getPieceOn()

            if (Position.areEqual(p.position, square)) return true;
        return false;
    }

    private Piece getPieceOn(Position p){
        for(Piece i : pieces) if(Position.areEqual(p, i.position)) return i;
        return new Pawn(context, (byte) 0); // protection for null pointer exception
    }

    private List<Position> getMovePointers(Piece p){
        List<Position> tempMovePointers = p.moveXY();
        ListIterator<Position> tempIterator = tempMovePointers.listIterator();
        int tempX, tempY, sigX, sigY;
        Position tempMovePointer;
        while (tempIterator.hasNext()) {
            tempMovePointer = tempIterator.next();
            if(tempMovePointer.x > 7 || tempMovePointer.x < 0 || tempMovePointer.y > 7 || tempMovePointer.y < 0){
                tempIterator.remove();
                continue;
            }
            if(pieceOnSquare(tempMovePointer)){
                tempIterator.remove();
                tempX = tempMovePointer.x; tempY = tempMovePointer.y;
                sigX = (int) Math.signum(p.position.x - tempX);
                sigY = (int) Math.signum(p.position.y - tempY);
                while(tempIterator.hasNext()){
                    tempMovePointer = tempIterator.next();
                    tempX = tempMovePointer.x; tempY = tempMovePointer.y;
                    if(sigX == Math.signum(p.position.x - tempX)
                            && sigY == Math.signum((p.position.y - tempY)))
                        tempIterator.remove();
                    else{
                        tempIterator.previous();
                        break;
                    }
                }
            }
        }
        if(p instanceof King)
            for(Piece rook : pieces) if(rook instanceof Rook && rook.color == p.color)
                tempMovePointers.addAll(castling(p, rook));

        return tempMovePointers;
    }

    private List<Position> getAttackPointers(Piece p){
        List<Position> tempAttackPointers = p.attackXY();
        ListIterator<Position> tempIterator = tempAttackPointers.listIterator();
        int tempX, tempY, sigX, sigY;

        Position tempAttackPointer;
        while (tempIterator.hasNext()) {
            tempAttackPointer = tempIterator.next();
            if (!pieceOnSquare(tempAttackPointer)) tempIterator.remove();
            else {
                if (getPieceOn(tempAttackPointer).color == p.color)
                    tempIterator.remove();
                tempX = tempAttackPointer.x;
                tempY = tempAttackPointer.y;
                sigX = (int) Math.signum(p.position.x - tempX);
                sigY = (int) Math.signum(p.position.y - tempY);
                while (tempIterator.hasNext()) {
                    tempAttackPointer = tempIterator.next();
                    tempX = tempAttackPointer.x;
                    tempY = tempAttackPointer.y;
                    if (sigX == Math.signum(p.position.x - tempX)
                            && sigY == Math.signum((p.position.y - tempY)))
                        tempIterator.remove();
                    else {
                        tempIterator.previous();
                        break;
                    }
                }
            }
        }

        if(p instanceof Pawn){
            Position enPosition = new Position(p.position.x+1, p.position.y);
            Piece enPiece;
            if(pieceOnSquare(enPosition)){
                enPiece = getPieceOn(enPosition);
                if (enPiece instanceof Pawn) if(enPiece.enPassant) {
                    if(p.color == Const.WHITE) enPosition.y++;
                    else enPosition.y--;
                    tempAttackPointers.add(enPosition);
                }
            }
            enPosition = new Position(p.position.x-1, p.position.y);
            if(pieceOnSquare(enPosition)){
                enPiece = getPieceOn(enPosition);
                if (enPiece instanceof Pawn) if(enPiece.enPassant) {
                    if(p.color == Const.WHITE) enPosition.y++;
                    else enPosition.y--;
                    tempAttackPointers.add(enPosition);
                }
            }
        }

        return tempAttackPointers;
    }

    private List<Position> castling(Piece king, Piece rook){
        List<Position> castling = new ArrayList<>();
        if(king.firstMove) if(rook.firstMove) {
            int step = (int) Math.signum(rook.position.x - king.position.x);
            boolean possible = true;
            Position square;
            for(int x = king.position.x+step; x != rook.position.x; x += step){
                square = new Position(x, king.position.y);
                if(pieceOnSquare(square)){
                    possible = false;
                    break;
                } else if(x != rook.position.x+step) if(isSquareAttacked(square, king)){
                    possible = false;
                    break;
                }
            }
            if(possible) castling.add(new Position(king.position.x + 2*step, king.position.y));
        }
        return castling;
    }

    private Piece getCloserRook(int x, int color){
        Piece rook = null;
        int i;
        for(i = 0; i < pieces.size(); i++)
            if(pieces.get(i).color == color) if(pieces.get(i) instanceof Rook){
            rook = pieces.get(i);
            break;
        }
        for(; i < pieces.size(); i++)
            if(pieces.get(i).color == color) if(pieces.get(i) instanceof Rook) {
                assert rook != null;
                if(Math.abs(x-rook.position.x) > Math.abs(x-pieces.get(i).position.x)){
                    rook = pieces.get(i);
                    break;
                }
            }

        return rook;
    }

    private boolean mayCheckBeAvoided(Piece king){
        List<Piece> tempPieces = new ArrayList<>(pieces);
        for(Piece i : tempPieces) { // needs to be like this, see README.md -> code tricks
            if (i.color == king.color) {
                if (i instanceof King) {
                    if (!removeAttacked(getMovePointers(i), i).isEmpty()) return true;
                    if (!removeAttacked(getAttackPointers(i), i).isEmpty()) return true;
                } else {
                    if (!makeKingSafe(i, getMovePointers(i)).isEmpty()) return true;
                    if (!makeKingSafe(i, getAttackPointers(i)).isEmpty()) return true;
                }
            }
        }
        return false;
    }

    private List<Position> makeKingSafe(Piece movingPiece, List<Position> squares){
        Piece king;
        if(movingPiece.color == Const.WHITE) king = whiteKing;
        else king = blackKing;
        return removeAttacked(squares, king, movingPiece);
    }

    private boolean isSquareAttacked(Position square, Piece protectedPiece){
        Piece capturedPiece = null;
        if(pieceOnSquare(square)) if(getPieceOn(square) != protectedPiece){
            capturedPiece = getPieceOn(square);
            pieces.remove(capturedPiece);
        }
        Position protectedPiecePosition = protectedPiece.position;
        protectedPiece.position = square;
        for(Piece i : pieces) if(i.color != protectedPiece.color) {
            for (Position attackedSquare : getAttackPointers(i))
                if (Position.areEqual(square, attackedSquare)) {
                    if (capturedPiece != null) pieces.add(capturedPiece);
                    protectedPiece.position = protectedPiecePosition;
                    return true;
                }
        }
        protectedPiece.position = protectedPiecePosition;
        if (capturedPiece != null) pieces.add(capturedPiece);
        return false;
    }

    // deleting attacked squares from kings moves
    private List<Position> removeAttacked(List<Position> squares, Piece protectedPiece){
        ListIterator<Position> squaresIterator = squares.listIterator();
        Position tempSquare;
        while(squaresIterator.hasNext()) {
            tempSquare = squaresIterator.next();
            if(isSquareAttacked(tempSquare, protectedPiece)) squaresIterator.remove();
        }
        return squares;
    }

    // overloaded for making King safe when other pieces move
    private List<Position> removeAttacked(List<Position> squares, Piece protectedPiece/*king*/, Piece movingPiece){
        ListIterator<Position> squaresIterator = squares.listIterator();
        Position movingPiecePosition = movingPiece.position, tempSquare;
        Piece deletedPiece = null;
        while(squaresIterator.hasNext()) {
            tempSquare = squaresIterator.next();
            if(pieceOnSquare(tempSquare)){
                deletedPiece = getPieceOn(tempSquare);
                pieces.remove(deletedPiece);
            }
            movingPiece.position = tempSquare;
            if(isSquareAttacked(protectedPiece.position, protectedPiece)) squaresIterator.remove();
            if(deletedPiece != null){
                pieces.add(deletedPiece);
                deletedPiece = null;
            }
        }
        movingPiece.position = movingPiecePosition;
        return squares;
    }

    private void capture(Piece capturedPiece){
        capturedPieces.add(capturedPiece);
        pieces.remove(capturedPiece);
        gameActivity.updatePads(capturedPiece);
    }

    private void promotion(Piece promotedPawn){
        pause();
        gameActivity.openPromotionFragment(promotedPawn.color);
    }

    void promotionAddPiece(int type){
        switch (type){
            case Const.KNIGHT:
                pieces.add(new Knight(context, activePiece.color, activePiece.position));
                break;

            case Const.BISHOP:
                pieces.add(new Bishop(context, activePiece.color, activePiece.position));
                break;

            case Const.ROOK:
                pieces.add(new Rook(context, activePiece.color, activePiece.position));
                break;

            case Const.QUEEN:
                pieces.add(new Queen(context, activePiece.color, activePiece.position));
                break;
            default:
                Log.v(Const.DEBUG_TAG, "game, openPromotionFragment - error int");
        }

        pieces.remove(activePiece);
        gameActivity.redrawBoard();
    }

    void pause(){
        previousState = state;
        state = Const.STATE_PAUSE;
        gameActivity.pauseGame();
    }

    void unpause(){
        if(state == Const.STATE_PAUSE) state = previousState;
        gameActivity.unpauseGame();
    }
}
