package common;

/**
 * Constantes do protocolo: opcodes e códigos de status. Usá-las no cliente e servidor
 */
public final class Protocol {
    private Protocol() {}

    // Opcodes (byte)
    public static final byte HELLO = 0x00;
    public static final byte REGISTER = 0x01;
    public static final byte LOGIN = 0x02;
    public static final byte LOGOUT = 0x03;

    public static final byte ADD_EVENT = 0x10;
    public static final byte ADVANCE_DAY = 0x11;

    public static final byte AGG_QUANTITY = 0x20;
    public static final byte AGG_VOLUME = 0x21;
    public static final byte AGG_AVG_PRICE = 0x22;
    public static final byte AGG_MAX_PRICE = 0x23;

    public static final byte FILTER_EVENTS = 0x30;

    public static final byte WAIT_SIMULTANEOUS = 0x40;
    public static final byte WAIT_CONSECUTIVE = 0x41;

    public static final byte HEARTBEAT = 0x50;

    public static final byte RESPONSE = 0x7F;     // genérico: status + payload
    public static final byte SERVER_PUSH = 0x70;  // requestId == 0

    // Response status codes (byte)
    public static final byte STATUS_OK = 0x00;
    public static final byte STATUS_AUTH_REQUIRED = 0x01;
    public static final byte STATUS_INVALID_CREDENTIALS = 0x02;
    public static final byte STATUS_ALREADY_EXISTS = 0x03;
    public static final byte STATUS_INVALID_REQUEST = 0x04;
    public static final byte STATUS_NOT_AUTHORIZED = 0x05;
    public static final byte STATUS_NOT_FOUND = 0x06;
    public static final byte STATUS_TIMEOUT = 0x07;
    public static final byte STATUS_INTERNAL_ERROR = 0x08;
    public static final byte STATUS_BAD_PROTOCOL_VERSION = 0x09;
    public static final byte STATUS_RESOURCE_LIMIT = 0x0A;
}