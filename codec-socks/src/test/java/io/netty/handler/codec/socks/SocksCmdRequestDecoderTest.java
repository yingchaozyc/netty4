/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.socks;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;
import sun.net.util.IPAddressUtil;

import static org.junit.Assert.*;

public class SocksCmdRequestDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksCmdRequestDecoderTest.class);

    private static void testSocksCmdRequestDecoderWithDifferentParams(SocksCmdType cmdType,
                                                                      SocksAddressType addressType,
                                                                      String host,
                                                                      int port) {
        logger.debug("Testing cmdType: " + cmdType + " addressType: " + addressType + " host: " + host +
                " port: " + port);
        SocksCmdRequest msg = new SocksCmdRequest(cmdType, addressType, host, port);
        SocksCmdRequestDecoder decoder = new SocksCmdRequestDecoder();
        EmbeddedChannel embedder = new EmbeddedChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (msg.addressType() == SocksAddressType.UNKNOWN) {
            assertTrue(embedder.readInbound() instanceof UnknownSocksRequest);
        } else {
            msg = (SocksCmdRequest) embedder.readInbound();
            assertSame(msg.cmdType(), cmdType);
            assertSame(msg.addressType(), addressType);
            assertEquals(msg.host(), host);
            assertEquals(msg.port(), port);
        }
        assertNull(embedder.readInbound());
    }

    @Test
    public void testCmdRequestDecoderIPv4() {
        String[] hosts = {"127.0.0.1", };
        int[] ports = {0, 32769, 65535 };
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.IPv4, host, port);
                }
            }
        }
    }

    @Test
    public void testCmdRequestDecoderIPv6() {
        String[] hosts = {SocksCommonUtils.ipv6toStr(IPAddressUtil.textToNumericFormatV6("::1"))};
        int[] ports = {0, 32769, 65535};
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            for (String host : hosts) {
                for (int port : ports) {
                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.IPv6, host, port);
                }
            }
        }
    }

//   @Test
//    public void testCmdRequestDecoderDomain() {
//        String[] hosts = {"google.com" ,
//                          "賲孬丕賱.廿禺鬲亘丕乇",
//                          "蟺伪蟻维未蔚喂纬渭伪.未慰魏喂渭萎",
//                          "賲孬丕賱.丌夭賲丕蹖卮蹖",
//                          "锌褉懈屑械褉.懈褋锌褘褌邪薪懈械",
//                          "讘撞址砖驻旨讬诇.讟注住讟",
//                          "渚嬪瓙.娴嬭瘯",
//                          "渚嬪瓙.娓│",
//                          "啶夃う啶距す啶班ぃ.啶ぐ啷�啷嵿し啶�,
//                          "渚嬨亪.銉嗐偣銉�,
//                          "鞁る.韰岇姢韸�,
//                          "喈夃喈距喈｀喁�喈喈苦疅喁嵿畾喁�};
//        int[] ports = {0, 32769, 65535};
//        for (SocksCmdType cmdType : SocksCmdType.values()) {
//            for (String host : hosts) {
//                for (int port : ports) {
//                    testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.DOMAIN, host, port);
//                }
//            }
//        }
//    }

    @Test
    public void testCmdRequestDecoderUnknown() {
        String host = "google.com";
        int port = 80;
        for (SocksCmdType cmdType : SocksCmdType.values()) {
            testSocksCmdRequestDecoderWithDifferentParams(cmdType, SocksAddressType.UNKNOWN, host, port);
        }
    }
}
