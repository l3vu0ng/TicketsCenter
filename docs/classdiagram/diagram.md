<mxfile host="app.diagrams.net">
  <diagram id="_VNGWM4onpKQ6BDNnxH7" name="Trang-1">
    <mxGraphModel dx="4417" dy="2297" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="0" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
      <root>
        <mxCell id="0" />
        <mxCell id="1" parent="0" />
        <mxCell id="diagram-title" parent="1" style="text;html=1;align=left;fontFamily=Arial;fontSize=22;fontStyle=1;fontColor=#334155;strokeColor=none;fillColor=none;" value="TicketsCenter - Class Diagram" vertex="1">
          <mxGeometry height="35" width="1000" x="40" y="20" as="geometry" />
        </mxCell>
        <mxCell id="pkg-identity" parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fillColor=#F8FAFC;swimlaneFillColor=#E0F2FE;strokeColor=#64748B;dashed=1;fontStyle=1;fontSize=14;align=center;verticalAlign=middle;fontColor=#0F172A;fontFamily=Arial;rounded=0;" value="IDENTITY &amp; ORGANIZATION" vertex="1">
          <mxGeometry height="780" width="1040" x="40" y="80" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;OrganizationRequest&lt;/b&gt;" id="class-organization-request">
          <mxCell parent="pkg-identity" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="299" width="440" x="570" y="250" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-organization-request-content" parent="class-organization-request" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- organizationName: String&lt;br&gt;- contactEmail: String&lt;br&gt;- contactPhone: String&lt;br&gt;- description: String&lt;br&gt;- status: OrganizationRequestStatus&lt;br&gt;- requestedAt: Instant&lt;br&gt;- decidedAt: Instant [0..1]&lt;br&gt;- rejectionReason: String [0..1]&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ approve(now: Instant): void&lt;br&gt;+ reject(reason: String, now: Instant): void&lt;br&gt;{PENDING / APPROVED / REJECTED}&lt;br&gt;{approval creates Organization and MANAGER membership atomically}" vertex="1">
          <mxGeometry height="267" width="440" y="32" as="geometry" />
        </mxCell>
        <mxCell id="pkg-event" parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fillColor=#F8FAFC;swimlaneFillColor=#DCFCE7;strokeColor=#64748B;dashed=1;fontStyle=1;fontSize=14;align=center;verticalAlign=middle;fontColor=#0F172A;fontFamily=Arial;rounded=0;" value="EVENT &amp; SEATING" vertex="1">
          <mxGeometry height="620" width="1860" x="1120" y="80" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Zone&lt;/b&gt;" id="class-zone">
          <mxCell parent="pkg-event" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="285" width="360" x="930" y="50" as="geometry">
              <mxRectangle height="32" width="70" x="830" y="50" as="alternateBounds" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <mxCell id="class-zone-content" parent="class-zone" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- name: String&lt;br&gt;- type: ZoneType&lt;br&gt;- price: BigDecimal&lt;br&gt;- standingCapacity: Integer [0..1]&lt;br&gt;- standingHeld: Integer [0..1]&lt;br&gt;- standingSold: Integer [0..1]&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ hold(qty: Integer): Boolean&lt;br&gt;+ release(qty: Integer): void&lt;br&gt;+ markSold(qty: Integer): void&lt;div&gt;+&amp;nbsp;&lt;code&gt;&lt;font face=&quot;Arial&quot;&gt;getCapacity():&lt;/font&gt;&lt;font face=&quot;Arial&quot;&gt; Integer&lt;/font&gt;&lt;/code&gt;&lt;br&gt;+ getAvailable(): Integer&lt;br&gt;{STANDING uses quota; SEATED counts AVAILABLE Seat}&lt;/div&gt;" vertex="1">
          <mxGeometry height="253" width="360" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Seat&lt;/b&gt;" id="class-seat">
          <mxCell parent="pkg-event" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="220" width="360" x="1390" y="50" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-seat-content" parent="class-seat" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- rowName: String&lt;br&gt;- seatNumber: String&lt;br&gt;- status: SeatStatus&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ hold(item: TicketHoldItem): void&lt;br&gt;+ release(item: TicketHoldItem): void&lt;br&gt;+ markSold(item: TicketHoldItem): void&lt;br&gt;{at most one ACTIVE HoldItem per Seat}" vertex="1">
          <mxGeometry height="188" width="360" y="32" as="geometry" />
        </mxCell>
        <mxCell id="rel-zone-seat" edge="1" parent="pkg-event" source="class-zone" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=1;exitY=0.3;exitDx=0;exitDy=0;entryX=0;entryY=0.37;entryDx=0;entryDy=0;" target="class-seat" value="contains">
          <mxGeometry relative="1" x="-1" y="19" as="geometry">
            <mxPoint as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-seating-zone-seating-row-source-multiplicity" connectable="0" parent="rel-zone-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-seating-zone-seating-row-target-multiplicity" connectable="0" parent="rel-zone-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" y="-21" as="offset" />
          </mxGeometry>
        </mxCell>
        <UserObject label="&lt;b&gt;Event&lt;/b&gt;" id="class-event">
          <mxCell parent="pkg-event" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="502" width="380" x="440" y="50" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-event-content" parent="class-event" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- title: String&lt;br&gt;- description: String&lt;br&gt;- venueName: String&lt;br&gt;- venueAddress: String&lt;br&gt;- startTime: Instant&lt;br&gt;- endTime: Instant&lt;br&gt;- saleStart: Instant&lt;br&gt;- saleEnd: Instant&lt;br&gt;- status: EventStatus&lt;br&gt;- rejectionReason: String [0..1]&lt;br&gt;- createdAt: Instant&lt;br&gt;- coverImageUrl: String [0..1]&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ submitForApproval(): void&lt;br&gt;+ publish(): void&lt;br&gt;+ reject(reason: String): void&lt;br&gt;+ cancel(): void&lt;br&gt;+ isSaleActive(now: Instant): Boolean&lt;br&gt;{saleStart &lt; saleEnd &lt;= startTime &lt; endTime}&lt;br&gt;{cover required before approval}&lt;br&gt;{published seating structure is immutable}&lt;br&gt;{cancel only before startTime}" vertex="1">
          <mxGeometry height="470" width="380" y="32" as="geometry" />
        </mxCell>
        <mxCell id="rel-event-zone" edge="1" parent="pkg-event" source="class-event" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=1;exitY=0.42;exitDx=0;exitDy=0;entryX=0;entryY=0.62;entryDx=0;entryDy=0;" target="class-zone" value="contains">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-event-zone-source-multiplicity" connectable="0" parent="rel-event-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" y="-11" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-event-zone-target-multiplicity" connectable="0" parent="rel-event-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" y="-17" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="pkg-ticketing" parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fillColor=#F8FAFC;swimlaneFillColor=#FEF3C7;strokeColor=#64748B;dashed=1;fontStyle=1;fontSize=14;align=center;verticalAlign=middle;fontColor=#0F172A;fontFamily=Arial;rounded=0;" value="TICKETING" vertex="1">
          <mxGeometry height="440" width="860" x="3050" y="80" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;TicketHoldItem&lt;/b&gt;" id="class-ticket-hold-item">
          <mxCell parent="pkg-ticketing" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="265" width="330" x="470" y="50" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-ticket-hold-item-content" parent="class-ticket-hold-item" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- quantity: Integer&lt;br&gt;- unitPrice: BigDecimal&lt;br&gt;{STANDING =&gt; seat absent and quantity &gt; 0}&lt;br&gt;{SEATED =&gt; seat present and quantity = 1}&lt;br&gt;{seat present =&gt; seat.zone = item.zone}&lt;br&gt;{item.zone.event = hold.event}&lt;br&gt;{one Item represents exactly one sale mode}" vertex="1">
          <mxGeometry height="233" width="330" y="32" as="geometry" />
        </mxCell>
        <mxCell id="pkg-order" parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fillColor=#F8FAFC;swimlaneFillColor=#FCE7F3;strokeColor=#64748B;dashed=1;fontStyle=1;fontSize=14;align=center;verticalAlign=middle;fontColor=#0F172A;fontFamily=Arial;rounded=0;" value="ORDER, PAYMENT &amp; PROMOTION" vertex="1">
          <mxGeometry height="730" width="1770" x="40" y="860" as="geometry" />
        </mxCell>
        <mxCell id="rel-order-item-seat" edge="1" parent="pkg-order" source="class-order-item" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-seat" value="assigns">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="620" y="-180" />
              <mxPoint x="2660" y="-180" />
            </Array>
            <mxPoint x="650" y="50" as="sourcePoint" />
            <mxPoint x="2660" y="-510" as="targetPoint" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-seat-source-multiplicity" connectable="0" parent="rel-order-item-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" y="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-seat-target-multiplicity" connectable="0" parent="rel-order-item-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="pkg-fulfillment" parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fillColor=#F8FAFC;swimlaneFillColor=#EDE9FE;strokeColor=#64748B;dashed=1;fontStyle=1;fontSize=14;align=center;verticalAlign=middle;fontColor=#0F172A;fontFamily=Arial;rounded=0;" value="TICKET, CHECK-IN &amp; REFUND" vertex="1">
          <mxGeometry height="700" width="900" x="1850" y="860" as="geometry" />
        </mxCell>
        <mxCell id="pkg-settlement" parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fillColor=#F8FAFC;swimlaneFillColor=#FFEDD5;strokeColor=#64748B;dashed=1;fontStyle=1;fontSize=14;align=center;verticalAlign=middle;fontColor=#0F172A;fontFamily=Arial;rounded=0;" value="SETTLEMENT &amp; AUDIT" vertex="1">
          <mxGeometry height="800" width="930" x="3130" y="860" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;User&lt;/b&gt;" id="class-user">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="299" width="300" x="70" y="130" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-user-content" parent="class-user" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- email: String&lt;br&gt;- passwordHash: String&lt;br&gt;- fullName: String&lt;br&gt;- phone: String [0..1]&lt;br&gt;- status: UserStatus&lt;br&gt;- createdAt: Instant&lt;br&gt;- platformRoles: PlatformRole [0..*]&lt;br&gt;- emailVerifiedAt: Instant [0..1]&lt;br&gt;- authVersion: Long&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ updateProfile(fullName: String, phone: String [0..1]): void&lt;br&gt;{verified email required before hold}" vertex="1">
          <mxGeometry height="267" width="300" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Organization&lt;/b&gt;" id="class-organization">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="165" width="320" x="400" y="130" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-organization-content" parent="class-organization" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- name: String&lt;br&gt;- createdAt: Instant&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ addMember(user: User, role: OrganizationRole): OrganizationMembership" vertex="1">
          <mxGeometry height="133" width="320" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;OrganizationMembership&lt;/b&gt;" id="class-organization-membership">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="170" width="300" x="750" y="130" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-organization-membership-content" parent="class-organization-membership" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- role: OrganizationRole&lt;br&gt;- active: Boolean&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ changeRole(role: OrganizationRole): void&lt;br&gt;+ deactivate(): void" vertex="1">
          <mxGeometry height="138" width="300" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;EventCategory&lt;/b&gt;" id="class-event-category">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="105" width="280" x="1150" y="130" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-event-category-content" parent="class-event-category" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- name: String" vertex="1">
          <mxGeometry height="73" width="280" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;TicketHold&lt;/b&gt;" id="class-ticket-hold">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="254" width="300" x="3080" y="130" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-ticket-hold-content" parent="class-ticket-hold" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- createdAt: Instant&lt;br&gt;- expiresAt: Instant&lt;br&gt;- status: HoldStatus&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ isExpired(now: Instant): Boolean&lt;br&gt;+ markReleased(): void&lt;br&gt;+ markConsumed(): void&lt;br&gt;{duration = 10 minutes; total quantity &lt;= 8}&lt;br&gt;{at most one ACTIVE hold per User}&lt;br&gt;{unit prices are frozen until expiration}" vertex="1">
          <mxGeometry height="222" width="300" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Order&lt;/b&gt;" id="class-order">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="290" width="400" x="70" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-order-content" parent="class-order" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- orderCode: String&lt;br&gt;- status: OrderStatus&lt;br&gt;- subtotalAmount: BigDecimal&lt;br&gt;- discountAmount: BigDecimal&lt;br&gt;- totalAmount: BigDecimal&lt;br&gt;- createdAt: Instant&lt;br&gt;- paidAt: Instant [0..1]&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ markPaid(payment: Payment [0..1], now: Instant): void&lt;br&gt;+ cancel(): void&lt;br&gt;+ expire(now: Instant): void" vertex="1">
          <mxGeometry height="258" width="400" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;OrderItem&lt;/b&gt;" id="class-order-item">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="265" width="400" x="500" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-order-item-content" parent="class-order-item" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- zoneNameSnapshot: String&lt;br&gt;- seatLabelSnapshot: String [0..1]&lt;br&gt;- quantity: Integer&lt;br&gt;- unitPrice: BigDecimal&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ getLineTotal(): BigDecimal&lt;br&gt;{STANDING =&gt; seat absent and quantity &gt; 0}&lt;br&gt;{SEATED =&gt; seat present and quantity = 1}&lt;br&gt;{seat present =&gt; seat.zone = item.zone}&lt;br&gt;{item.zone.event = order.event}" vertex="1">
          <mxGeometry height="233" width="400" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Payment&lt;/b&gt;" id="class-payment">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="270" width="400" x="930" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-payment-content" parent="class-payment" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- amount: BigDecimal&lt;br&gt;- status: PaymentStatus&lt;br&gt;- createdAt: Instant&lt;br&gt;- txnRef: String&lt;br&gt;- transactionNo: String [0..1]&lt;br&gt;- paidAt: Instant [0..1]&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ markCaptured(transactionNo: String, paidAt: Instant): void&lt;br&gt;+ markFailed(): void&lt;br&gt;+ markUnknown(): void" vertex="1">
          <mxGeometry height="238" width="400" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Coupon&lt;/b&gt;" id="class-coupon">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="352" width="400" x="1360" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-coupon-content" parent="class-coupon" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- code: String&lt;br&gt;- discountType: DiscountType&lt;br&gt;- fixedAmount: BigDecimal [0..1]&lt;br&gt;- percentage: BigDecimal [0..1]&lt;br&gt;- validFrom: Instant&lt;br&gt;- validTo: Instant&lt;br&gt;- isActive: Boolean&lt;br&gt;- maxUses: Integer&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ isValid(now: Instant): Boolean&lt;br&gt;+ calculateDiscount(subtotal: BigDecimal): BigDecimal&lt;br&gt;{0 &lt; percentage &lt;= 30 when PERCENTAGE}&lt;br&gt;{discount &lt;= 30% of subtotal}&lt;br&gt;{reserved + consumed redemptions &lt;= maxUses}" vertex="1">
          <mxGeometry height="320" width="400" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Ticket&lt;/b&gt;" id="class-ticket">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="280" width="280" x="1880" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-ticket-content" parent="class-ticket" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- ticketCode: String&lt;br&gt;- status: TicketStatus&lt;br&gt;- issuedAt: Instant&lt;br&gt;- paidAmount: BigDecimal&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ markUsed(): void&lt;br&gt;+ markRefundPending(): void&lt;br&gt;+ markRefunded(): void&lt;br&gt;+ invalidate(): void&lt;br&gt;+ generateQRCode(): byte[]&lt;br&gt;+ restoreActive(): void" vertex="1">
          <mxGeometry height="248" width="280" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;CheckIn&lt;/b&gt;" id="class-check-in">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="115" width="220" x="2190" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-check-in-content" parent="class-check-in" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- result: CheckInResult&lt;br&gt;- scannedAt: Instant" vertex="1">
          <mxGeometry height="83" width="220" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;RefundRequest&lt;/b&gt;" id="class-refund-request">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="430" width="280" x="2440" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-refund-request-content" parent="class-refund-request" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- reason: String&lt;br&gt;- status: RefundRequestStatus&lt;br&gt;- requestedAt: Instant&lt;br&gt;- decidedAt: Instant [0..1]&lt;br&gt;- reasonType: RefundRequestReason&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ approve(now: Instant): void&lt;br&gt;+ reject(now: Instant): void&lt;br&gt;+ markCompleted(): void&lt;br&gt;+ getRequestedAmount(): BigDecimal&lt;br&gt;{all Ticket belong to one Order}&lt;br&gt;{before Event.startTime; unused Ticket}&lt;br&gt;{amount = sum(Ticket.paidAmount)}&lt;br&gt;{at most one open Request per Ticket}&lt;br&gt;{CUSTOMER_REQUEST or EVENT_CANCELLATION}&lt;br&gt;{cancellation requests are auto-approved}" vertex="1">
          <mxGeometry height="398" width="280" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Refund&lt;/b&gt;" id="class-refund">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="270" width="360" x="1880" y="1230" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-refund-content" parent="class-refund" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- amount: BigDecimal&lt;br&gt;- purpose: RefundPurpose&lt;br&gt;- status: RefundStatus&lt;br&gt;- createdAt: Instant&lt;br&gt;- providerReference: String [0..1]&lt;br&gt;- processedAt: Instant [0..1]&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ markSucceeded(providerReference: String, now: Instant): void&lt;br&gt;+ markFailed(now: Instant): void&lt;br&gt;+ markUnknown(now: Instant): void&lt;br&gt;{Request absent only for payment compensation}" vertex="1">
          <mxGeometry height="238" width="360" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;CommissionRule&lt;/b&gt;" id="class-commission-rule">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="300" width="250" x="3160" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-commission-rule-content" parent="class-commission-rule" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- ratePercent: BigDecimal&lt;br&gt;- fixedFee: BigDecimal&lt;br&gt;- effectiveFrom: Instant&lt;br&gt;- effectiveTo: Instant&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ calculateFee(amount: BigDecimal): BigDecimal&lt;br&gt;{managed by platform admin for Organization}&lt;br&gt;{rate and fixed fee immutable once assigned to Event}" vertex="1">
          <mxGeometry height="268" width="250" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Settlement&lt;/b&gt;" id="class-settlement">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#FDE68A;strokeColor=#D97706;fontColor=#0F172A;fontFamily=Arial;strokeWidth=2;" vertex="1">
            <mxGeometry height="440" width="250" x="3440" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-settlement-content" parent="class-settlement" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- createdAt: Instant&lt;br&gt;- confirmedAt: Instant [0..1]&lt;br&gt;- grossRevenue: BigDecimal&lt;br&gt;- totalCommission: BigDecimal&lt;br&gt;- totalRefund: BigDecimal&lt;br&gt;- netPayable: BigDecimal&lt;br&gt;- status: SettlementStatus&lt;br&gt;&lt;hr&gt;&lt;br&gt;+ recalculate(): void&lt;br&gt;+ confirm(now: Instant): void&lt;br&gt;+ markPaid(): void&lt;br&gt;{recalculate only before confirmation}&lt;br&gt;{confirmed amounts ignore later CommissionRule changes}&lt;br&gt;{confirm after event end; no unresolved transactions}&lt;br&gt;{uses Event commission rule}" vertex="1">
          <mxGeometry height="408" width="250" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;SettlementItem&lt;/b&gt;" id="class-settlement-item">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="165" width="250" x="3720" y="910" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-settlement-item-content" parent="class-settlement-item" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- grossAmount: BigDecimal&lt;br&gt;- commissionAmount: BigDecimal&lt;br&gt;- refundAmount: BigDecimal&lt;br&gt;- netAmount: BigDecimal" vertex="1">
          <mxGeometry height="133" width="250" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;Payout&lt;/b&gt;" id="class-payout">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="165" width="250" x="3160" y="1280" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-payout-content" parent="class-payout" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- amount: BigDecimal&lt;br&gt;- reference: String [0..1]&lt;br&gt;- status: PayoutStatus&lt;br&gt;- paidAt: Instant [0..1]" vertex="1">
          <mxGeometry height="133" width="250" y="32" as="geometry" />
        </mxCell>
        <UserObject label="&lt;b&gt;AuditLog&lt;/b&gt;" id="class-audit-log">
          <mxCell parent="1" style="swimlane;html=1;startSize=32;horizontal=1;fontStyle=1;fontSize=14;align=center;swimlaneFillColor=#FFFFFF;fillColor=#E2E8F0;strokeColor=#64748B;fontColor=#0F172A;fontFamily=Arial;strokeWidth=1;" vertex="1">
            <mxGeometry height="205" width="320" x="3440" y="1410" as="geometry" />
          </mxCell>
        </UserObject>
        <mxCell id="class-audit-log-content" parent="class-audit-log" style="text;html=1;align=left;verticalAlign=top;spacingLeft=9;spacingTop=6;fontSize=13;strokeColor=none;fillColor=none;whiteSpace=wrap;overflow=hidden;fontFamily=Arial;fontColor=#334155;" value="- id: UUID&lt;br&gt;- action: String&lt;br&gt;- aggregateType: String&lt;br&gt;- aggregateId: UUID&lt;br&gt;- detail: String&lt;br&gt;- createdAt: Instant" vertex="1">
          <mxGeometry height="173" width="320" y="32" as="geometry" />
        </mxCell>
        <mxCell id="rel-user-organization-membership" edge="1" parent="1" source="class-user" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-organization-membership" value="participates_as">
          <mxGeometry relative="1" x="-0.329" as="geometry">
            <mxPoint as="offset" />
            <Array as="points">
              <mxPoint x="250" y="90" />
              <mxPoint x="900" y="90" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-user-organization-membership-source-multiplicity" connectable="0" parent="rel-user-organization-membership" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" y="5" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-user-organization-membership-target-multiplicity" connectable="0" parent="rel-user-organization-membership" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-organization-membership" edge="1" parent="1" source="class-organization" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=1;exitY=0.45;exitDx=0;exitDy=0;entryX=0;entryY=0.45;entryDx=0;entryDy=0;" target="class-organization-membership" value="has_members">
          <mxGeometry relative="1" x="-1" y="31" as="geometry">
            <mxPoint x="-10" y="2" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-organization-membership-source-multiplicity" connectable="0" parent="rel-organization-organization-membership" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry" />
        </mxCell>
        <mxCell id="rel-organization-organization-membership-target-multiplicity" connectable="0" parent="rel-organization-organization-membership" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-user-order" edge="1" parent="1" source="class-user" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-order" value="places">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="30" y="200" />
              <mxPoint x="30" y="1055" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-user-order-source-multiplicity" connectable="0" parent="rel-user-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-user-order-target-multiplicity" connectable="0" parent="rel-user-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-20" y="-25" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-user-ticket-hold" edge="1" parent="1" source="class-user" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0.5;exitY=0;exitDx=0;exitDy=0;" target="class-ticket-hold" value="creates">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="220" y="65.06" />
              <mxPoint x="3240" y="65.06" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-user-ticket-hold-source-multiplicity" connectable="0" parent="rel-user-ticket-hold" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" y="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-user-ticket-hold-target-multiplicity" connectable="0" parent="rel-user-ticket-hold" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-event" edge="1" parent="1" source="class-organization" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-event" value="organizes">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="560" y="110" />
              <mxPoint x="1750" y="110" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-event-source-multiplicity" connectable="0" parent="rel-organization-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-event-target-multiplicity" connectable="0" parent="rel-organization-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-coupon" edge="1" parent="1" source="class-organization" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;entryX=0.5;entryY=0;entryDx=0;entryDy=0;" target="class-coupon" value="issues">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="520" y="780" />
              <mxPoint x="1560" y="780" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-coupon-source-multiplicity" connectable="0" parent="rel-organization-coupon" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-coupon-target-multiplicity" connectable="0" parent="rel-organization-coupon" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-commission-rule" edge="1" parent="1" source="class-organization" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;entryX=0.5;entryY=0;entryDx=0;entryDy=0;" target="class-commission-rule" value="has_policy">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="430" y="820" />
              <mxPoint x="3310" y="820" />
              <mxPoint x="3310" y="910" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-commission-rule-source-multiplicity" connectable="0" parent="rel-organization-commission-rule" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-commission-rule-target-multiplicity" connectable="0" parent="rel-organization-commission-rule" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-event-event-category" edge="1" parent="1" source="class-event" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0;exitY=0.18;exitDx=0;exitDy=0;entryX=1;entryY=0.5;entryDx=0;entryDy=0;" target="class-event-category" value="categorized_by">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-event-event-category-source-multiplicity" connectable="0" parent="rel-event-event-category" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry" />
        </mxCell>
        <mxCell id="rel-event-event-category-target-multiplicity" connectable="0" parent="rel-event-event-category" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-ticket-hold-event" edge="1" parent="1" source="class-ticket-hold" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-event" value="for_event">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3230" y="660" />
              <mxPoint x="1840" y="660" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-event-source-multiplicity" connectable="0" parent="rel-ticket-hold-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-event-target-multiplicity" connectable="0" parent="rel-ticket-hold-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-ticket-hold-item" edge="1" parent="1" source="class-ticket-hold" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=1;exitY=0.45;exitDx=0;exitDy=0;entryX=0;entryY=0.35;entryDx=0;entryDy=0;" target="class-ticket-hold-item" value="reserves">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-ticket-hold-ticket-hold-item-source-multiplicity" connectable="0" parent="rel-ticket-hold-ticket-hold-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" y="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-ticket-hold-item-target-multiplicity" connectable="0" parent="rel-ticket-hold-ticket-hold-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint y="-13" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-zone" edge="1" parent="1" source="class-ticket-hold-item" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-zone" value="reserves_in">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3550" y="480" />
              <mxPoint x="2360" y="480" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-zone-source-multiplicity" connectable="0" parent="rel-ticket-hold-item-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-20" y="15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-zone-target-multiplicity" connectable="0" parent="rel-ticket-hold-item-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" y="5" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-seat" edge="1" parent="1" source="class-ticket-hold-item" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-seat" value="selects">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3630" y="620" />
              <mxPoint x="2780" y="620" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-seat-source-multiplicity" connectable="0" parent="rel-ticket-hold-item-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-20" y="15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-seat-target-multiplicity" connectable="0" parent="rel-ticket-hold-item-seat" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-order-item" edge="1" parent="1" source="class-ticket-hold-item" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0.5;exitY=1;exitDx=0;exitDy=0;" target="class-order-item" value="materializes_as">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3685" y="800" />
              <mxPoint x="680" y="800" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-order-item-source-multiplicity" connectable="0" parent="rel-ticket-hold-item-order-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="15" y="15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-item-order-item-target-multiplicity" connectable="0" parent="rel-ticket-hold-item-order-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-order-item" edge="1" parent="1" source="class-order" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=1;exitY=0.42;exitDx=0;exitDy=0;entryX=0;entryY=0.42;entryDx=0;entryDy=0;" target="class-order-item" value="contains">
          <mxGeometry relative="1" x="-1" y="20" as="geometry">
            <mxPoint as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-order-item-source-multiplicity" connectable="0" parent="rel-order-order-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-order-item-target-multiplicity" connectable="0" parent="rel-order-order-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint y="-11" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-zone" edge="1" parent="1" source="class-order-item" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;entryX=0.5;entryY=1;entryDx=0;entryDy=0;" target="class-zone" value="purchases_in">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="630" y="700" />
              <mxPoint x="2230" y="700" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-zone-source-multiplicity" connectable="0" parent="rel-order-item-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-zone-target-multiplicity" connectable="0" parent="rel-order-item-zone" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-ticket" edge="1" parent="1" source="class-order-item" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;entryX=0.5;entryY=0;entryDx=0;entryDy=0;" target="class-ticket" value="issues">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="730" y="840" />
              <mxPoint x="2020" y="840" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-ticket-source-multiplicity" connectable="0" parent="rel-order-item-ticket" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-item-ticket-target-multiplicity" connectable="0" parent="rel-order-item-ticket" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-payment" edge="1" parent="1" source="class-order" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;entryX=0.5;entryY=1;entryDx=0;entryDy=0;" target="class-payment" value="payment_attempts">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="310" y="1480" />
              <mxPoint x="1130" y="1480" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-payment-source-multiplicity" connectable="0" parent="rel-order-payment" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-payment-target-multiplicity" connectable="0" parent="rel-order-payment" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-coupon" edge="1" parent="1" source="class-order" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;entryX=0.5;entryY=1;entryDx=0;entryDy=0;" target="class-coupon" value="applies">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="360" y="1410" />
              <mxPoint x="1560" y="1410" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-coupon-source-multiplicity" connectable="0" parent="rel-order-coupon" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-coupon-target-multiplicity" connectable="0" parent="rel-order-coupon" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-ticket" edge="1" parent="1" source="class-check-in" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0;exitY=0.45;exitDx=0;exitDy=0;entryX=1;entryY=0.28;entryDx=0;entryDy=0;" target="class-ticket" value="verifies">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-check-in-ticket-source-multiplicity" connectable="0" parent="rel-check-in-ticket" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint y="-12" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-ticket-target-multiplicity" connectable="0" parent="rel-check-in-ticket" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" y="12" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-event" edge="1" parent="1" source="class-check-in" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0.5;exitY=0;exitDx=0;exitDy=0;" target="class-event" value="during">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="2300" y="720" />
              <mxPoint x="1670" y="720" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-event-source-multiplicity" connectable="0" parent="rel-check-in-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-event-target-multiplicity" connectable="0" parent="rel-check-in-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-organization-membership" edge="1" parent="1" source="class-check-in" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-organization-membership" value="performed_by">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="2210" y="850" />
              <mxPoint x="1100" y="850" />
              <mxPoint x="1100" y="215.06" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-organization-membership-source-multiplicity" connectable="0" parent="rel-check-in-organization-membership" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-check-in-organization-membership-target-multiplicity" connectable="0" parent="rel-check-in-organization-membership" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-order" edge="1" parent="1" source="class-refund-request" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0.5;exitY=1;exitDx=0;exitDy=0;entryX=0.5;entryY=1;entryDx=0;entryDy=0;" target="class-order" value="against">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="2580" y="1580" />
              <mxPoint x="270" y="1580" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-order-source-multiplicity" connectable="0" parent="rel-refund-request-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-order-target-multiplicity" connectable="0" parent="rel-refund-request-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-ticket" edge="1" parent="1" source="class-refund-request" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0;exitY=0.5;exitDx=0;exitDy=0;entryX=1;entryY=0.5;entryDx=0;entryDy=0;" target="class-ticket" value="requests">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-refund-request-ticket-source-multiplicity" connectable="0" parent="rel-refund-request-ticket" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint y="13" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-ticket-target-multiplicity" connectable="0" parent="rel-refund-request-ticket" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" y="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-refund" edge="1" parent="1" source="class-refund-request" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-refund" value="executes">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="2510" y="1365" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-refund-source-multiplicity" connectable="0" parent="rel-refund-request-refund" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-request-refund-target-multiplicity" connectable="0" parent="rel-refund-request-refund" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" y="-5" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-payment" edge="1" parent="1" source="class-refund" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-payment" value="reverses">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="1860" y="1386.57" />
              <mxPoint x="1860" y="1500" />
              <mxPoint x="1230" y="1500" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-payment-source-multiplicity" connectable="0" parent="rel-refund-payment" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" y="-17" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-refund-payment-target-multiplicity" connectable="0" parent="rel-refund-payment" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="11" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-settlement-item" edge="1" parent="1" source="class-settlement" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=1;exitY=0.35;exitDx=0;exitDy=0;" target="class-settlement-item" value="aggregates">
          <mxGeometry relative="1" x="0.619" y="33" as="geometry">
            <mxPoint x="3" y="-13" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-settlement-item-source-multiplicity" connectable="0" parent="rel-settlement-settlement-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-settlement-item-target-multiplicity" connectable="0" parent="rel-settlement-settlement-item" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-settlement-item-order" edge="1" parent="1" source="class-settlement-item" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0.5;exitY=1;exitDx=0;exitDy=0;" target="class-order" value="reconciles">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3845" y="1640" />
              <mxPoint x="230" y="1640" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-item-order-source-multiplicity" connectable="0" parent="rel-settlement-item-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry" />
        </mxCell>
        <mxCell id="rel-settlement-item-order-target-multiplicity" connectable="0" parent="rel-settlement-item-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-payout" edge="1" parent="1" source="class-settlement" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;startArrow=diamondThin;startFill=1;endArrow=none;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;entryX=0.5;entryY=0;entryDx=0;entryDy=0;" target="class-payout" value="distributes">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3285" y="1265" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-payout-source-multiplicity" connectable="0" parent="rel-settlement-payout" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-payout-target-multiplicity" connectable="0" parent="rel-settlement-payout" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-15" y="-10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-commission-rule" edge="1" parent="1" source="class-settlement" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-commission-rule" value="applies_rule">
          <mxGeometry relative="1" x="1" y="-18" as="geometry">
            <mxPoint x="-10" y="3" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-commission-rule-source-multiplicity" connectable="0" parent="rel-settlement-commission-rule" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry" />
        </mxCell>
        <mxCell id="rel-settlement-commission-rule-target-multiplicity" connectable="0" parent="rel-settlement-commission-rule" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" y="5" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-audit-log-user" edge="1" parent="1" source="class-audit-log" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-user" value="actor">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="20" y="1540" />
              <mxPoint x="20" y="280" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-audit-log-user-source-multiplicity" connectable="0" parent="rel-audit-log-user" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-audit-log-user-target-multiplicity" connectable="0" parent="rel-audit-log-user" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-20" y="-12" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-event" edge="1" parent="1" source="class-order" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-event" value="for_event">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="270" y="760" />
              <mxPoint x="1630" y="760" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-event-source-multiplicity" connectable="0" parent="rel-order-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-order-event-target-multiplicity" connectable="0" parent="rel-order-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-order" edge="1" parent="1" source="class-ticket-hold" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-order" value="creates">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3100" y="740" />
              <mxPoint x="400" y="740" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-order-source-multiplicity" connectable="0" parent="rel-ticket-hold-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-ticket-hold-order-target-multiplicity" connectable="0" parent="rel-ticket-hold-order" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-10" y="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-event" edge="1" parent="1" source="class-settlement" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0.5;exitY=0;exitDx=0;exitDy=0;" target="class-event" value="reconciles">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="3565" y="780" />
              <mxPoint x="1720" y="780" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-event-source-multiplicity" connectable="0" parent="rel-settlement-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry">
            <mxPoint x="15" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-settlement-event-target-multiplicity" connectable="0" parent="rel-settlement-event" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="10" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-event-commission-rule" edge="1" parent="1" source="class-event" style="edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=open;endFill=0;strokeColor=#475569;" target="class-commission-rule" value="locks_on_publication">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="1750" y="805" />
              <mxPoint x="3280" y="805" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-event-commission-rule-source-multiplicity" connectable="0" parent="rel-event-commission-rule" style="edgeLabel;html=1;align=center;fontSize=12;fillColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-0.85" y="-14" as="geometry">
            <mxPoint x="34" y="-148" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-event-commission-rule-target-multiplicity" connectable="0" parent="rel-event-commission-rule" style="edgeLabel;html=1;align=center;fontSize=12;fillColor=#FFFFFF;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="0.85" y="-14" as="geometry" />
        </mxCell>
        <mxCell id="rel-organization-request-submitter" edge="1" parent="1" source="class-organization-request" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0;exitY=0.22;entryX=1;entryY=0.76;" target="class-user" value="submitted_by">
          <mxGeometry relative="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-organization-request-submitter-source-multiplicity" connectable="0" parent="rel-organization-request-submitter" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;fillColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry" />
        </mxCell>
        <mxCell id="rel-organization-request-submitter-target-multiplicity" connectable="0" parent="rel-organization-request-submitter" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;fillColor=#FFFFFF;" value="1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry" />
        </mxCell>
        <mxCell id="rel-organization-request-reviewer" edge="1" parent="1" source="class-organization-request" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;" target="class-user-content" value="reviewed_by">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="304" y="572" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-request-reviewer-source-multiplicity" connectable="0" parent="rel-organization-request-reviewer" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;fillColor=#FFFFFF;" value="0..*" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry" />
        </mxCell>
        <mxCell id="rel-organization-request-reviewer-target-multiplicity" connectable="0" parent="rel-organization-request-reviewer" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;fillColor=#FFFFFF;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="-34" as="offset" />
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-request-organization" edge="1" parent="1" source="class-organization-request" style="edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=open;endFill=0;strokeWidth=1.5;jumpStyle=arc;jumpSize=10;strokeColor=#475569;exitX=0.5;exitY=0;" target="class-organization" value="creates">
          <mxGeometry relative="1" as="geometry">
            <Array as="points">
              <mxPoint x="830" y="315" />
              <mxPoint x="560" y="315" />
            </Array>
          </mxGeometry>
        </mxCell>
        <mxCell id="rel-organization-request-organization-source-multiplicity" connectable="0" parent="rel-organization-request-organization" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;fillColor=#FFFFFF;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="-1" as="geometry" />
        </mxCell>
        <mxCell id="rel-organization-request-organization-target-multiplicity" connectable="0" parent="rel-organization-request-organization" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=11;fontColor=#334155;fillColor=#FFFFFF;" value="0..1" vertex="1">
          <mxGeometry relative="1" x="1" as="geometry">
            <mxPoint x="20" as="offset" />
          </mxGeometry>
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>
