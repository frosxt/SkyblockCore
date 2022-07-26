package me.illusion.skyblockcore.shared.packet.impl.proxytoinstance;

import lombok.Getter;
import me.illusion.skyblockcore.shared.data.IslandInvite;
import me.illusion.skyblockcore.shared.packet.data.ProxyToServerPacket;

@Getter
public class PacketInviteResponse extends ProxyToServerPacket {

    private final IslandInvite invite;
    private final Response response;

    public PacketInviteResponse(byte[] bytes) {
        super(bytes);

        invite = new IslandInvite(readUUID(), readUUID(), readString(), readUUID(), readString(), readLong());
        response = Response.getResponse(readInt());
    }

    public PacketInviteResponse(IslandInvite invite, Response response) {
        super((String) null);

        this.invite = invite;
        this.response = response;

        write();
    }

    @Override
    public void write() {
        writeUUID(invite.getInviteId());
        writeUUID(invite.getSender());
        writeString(invite.getSenderName());
        writeUUID(invite.getTarget());
        writeString(invite.getTargetName());
        writeLong(invite.getExpirationEpoch());
        writeInt(response.ordinal());
    }

    public enum Response {
        INVITE_ACCEPTED,
        INVITE_DENIED;

        private static final Response[] VALUES = values();

        public static Response getResponse(int id) {
            return VALUES[id];
        }

    }
}

