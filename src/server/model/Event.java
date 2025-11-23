package server.model;

import common.IOUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 Modelo para um evento de venda.
 */
public final class Event {
    private final String productName;
    private final int quantity;
    private final double price;
    private final long timestamp;

    public Event(String productName, int quantity, double price, long timestamp) {
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Serializa o evento para o stream usando IOUtils para a string.
    public void writeTo(DataOutputStream out) throws IOException {
        IOUtils.writeString(out, productName);
        out.writeInt(quantity);
        out.writeDouble(price);
        out.writeLong(timestamp);
    }

    // Lê um evento do stream que foi escrito com writeTo. Retorna uma nova instância Event.
    public static Event readFrom(DataInputStream in) throws IOException {
        String product = IOUtils.readString(in);
        int qty = in.readInt();
        double price = in.readDouble();
        long ts = in.readLong();
        return new Event(product, qty, price, ts);
    }

    @Override
    public String toString() {
        return "Event{" +
                "productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Event event = (Event) o;
        return quantity == event.quantity
                && Double.compare(event.price, price) == 0
                && timestamp == event.timestamp
                && Objects.equals(productName, event.productName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productName, quantity, price, timestamp);
    }
}