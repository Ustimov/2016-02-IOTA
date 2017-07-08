package su.iota.backend.models.game;

public final class Card extends FieldItem {

    {
        concrete = true;
        materialize();
    }

    public Card(Color color, Shape shape, Number number) {
        this.color = color;
        this.shape = shape;
        this.number = number;
    }

}
