package su.iota.backend.models.game;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;

public class Coordinate {

    @Expose
    private final int offX;

    @Expose
    private final int offY;

    public static final int BASE_X = Field.FIELD_DIMENSION / 2;
    public static final int BASE_Y = Field.FIELD_DIMENSION / 2;

    public Coordinate(int offX, int offY) {
        this.offX = offX;
        this.offY = offY;
    }

    public static Coordinate fromRaw(int x, int y) {
        return new Coordinate(x - BASE_X, y - BASE_Y);
    }

    public int getOffX() {
        return offX;
    }

    public int getOffY() {
        return offY;
    }

    public int getX() {
        return BASE_X + offX;
    }

    public int getY() {
        return BASE_Y + offY;
    }

    @NotNull
    public Coordinate plus(@NotNull Coordinate other) {
        return new Coordinate(offX + other.offX, offY + other.offY);
    }

    @NotNull
    public Coordinate minus(@NotNull Coordinate other) {
        return new Coordinate(offX - other.offX, offY - other.offY);
    }

    @SuppressWarnings("MagicNumber")
    public boolean isInRange() {
        final int x = getX();
        final int y = getY();
        boolean isOk = true;
        //noinspection ConstantConditions
        isOk = isOk && (x > 0 && x < Field.FIELD_DIMENSION - 1);
        isOk = isOk && (y > 0 && y < Field.FIELD_DIMENSION - 1);
        return isOk;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Coordinate other = (Coordinate) obj;
        return offX == other.offX && offY == other.offY;
    }

    @Override
    public int hashCode() {
        int result = offX;
        result = 31 * result + offY;
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "Coordinate{" +
                "offX=" + offX +
                ", offY=" + offY +
                '}';
    }

}
