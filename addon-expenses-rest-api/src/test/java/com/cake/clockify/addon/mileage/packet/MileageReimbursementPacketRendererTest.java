package com.cake.clockify.addon.mileage.packet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MileageReimbursementPacketRendererTest {

    @Test
    void rendersSummaryRowsAndEscapesText() {
        MileageReimbursementPacket packet = new MileageReimbursementPacket(
                "Ada <Lovelace>",
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-31"),
                Instant.parse("2026-06-16T12:00:00Z"),
                List.of(new MileageReimbursementPacketRow(
                        "2026-05-24",
                        "user-1",
                        "Ada <Lovelace>",
                        "project-1",
                        "North <Route>",
                        "exp-1",
                        "3.5",
                        "0.725",
                        "POLICY",
                        null,
                        "2026 <rate>",
                        "25.38585",
                        "25.39",
                        "",
                        "",
                        "FAILED",
                        "Clockify <failed>",
                        "",
                        "ERR",
                        "",
                        "",
                        "HQ <North>",
                        "Client <Site>",
                        "Install <support>",
                        "1200",
                        "1225",
                        "Storm <exception>")),
                new BigDecimal("3.5"),
                new BigDecimal("25.38585"),
                new BigDecimal("25.39"),
                1,
                1,
                List.of("2026 <rate>"),
                true);

        String html = MileageReimbursementPacketRenderer.render(packet);

        assertThat(html).contains("Mileage reimbursement packet");
        assertThat(html).contains("Ada &lt;Lovelace&gt;");
        assertThat(html).contains("North &lt;Route&gt;");
        assertThat(html).contains("HQ &lt;North&gt;");
        assertThat(html).contains("Client &lt;Site&gt;");
        assertThat(html).contains("Install &lt;support&gt;");
        assertThat(html).contains("1200 to 1225");
        assertThat(html).contains("Storm &lt;exception&gt;");
        assertThat(html).contains("2026 &lt;rate&gt;");
        assertThat(html).contains("Clockify &lt;failed&gt;");
        assertThat(html).contains("<dt>Calculated total</dt><dd>25.39</dd>");
        assertThat(html).contains("Packet row limit reached");
        assertThat(html).contains("<link rel=\"stylesheet\" href=\"/assets/mileage/packet.css\">");
        assertThat(html).contains("src=\"/assets/mileage/packet.js\" defer");
        assertThat(html).doesNotContain("<style");
        assertThat(html).doesNotContain("onclick=");
    }
}
