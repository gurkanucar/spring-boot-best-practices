package com.gucardev.jwtauthrefreshtokenroles.otp.delivery;

import com.gucardev.jwtauthrefreshtokenroles.otp.service.OtpChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

@Component
public class OtpSenderRegistry {

    private final Map<OtpChannel, OtpSender> senders;

    /** Fails at startup when a channel has no sender or more than one. */
    public OtpSenderRegistry(List<OtpSender> all) {
        Map<OtpChannel, OtpSender> map = new EnumMap<>(OtpChannel.class);
        for (OtpSender sender : all) {
            OtpSender previous = map.putIfAbsent(sender.channel(), sender);
            if (previous != null) {
                throw new IllegalStateException("Two OtpSenders for channel %s: %s and %s".formatted(
                        sender.channel(), name(previous), name(sender)));
            }
        }
        for (OtpChannel channel : OtpChannel.values()) {
            if (!map.containsKey(channel)) {
                throw new IllegalStateException("No OtpSender for channel " + channel);
            }
        }
        this.senders = Map.copyOf(map);
    }

    public OtpSender forChannel(OtpChannel channel) {
        return senders.get(channel);
    }

    private static String name(OtpSender sender) {
        return ClassUtils.getUserClass(sender).getSimpleName();
    }
}
