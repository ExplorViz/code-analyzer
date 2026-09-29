package net.explorviz.code.analysis.handler;

import com.google.protobuf.GeneratedMessageV3;

/**
 * Creates a Protobuffer data message from the object.
 *
 * @param <T> generated message type from protoBuffer
 */
public interface ProtoBufConvertable<T extends GeneratedMessageV3> {

  T getProtoBufObject();
}
