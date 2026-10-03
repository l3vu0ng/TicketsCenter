/**
 * TicketsCenter Seed Data & State Machine Store
 * Conforming strictly to SPEC.md domain models & business rules
 */

const SEED_DATA = {
  // 4. Vai trò & Tài khoản mẫu
  users: [
    {
      id: "u-001",
      email: "buyer@ticketscenter.vn",
      name: "Nguyễn Văn Hùng",
      emailVerified: true,
      emailVerifiedAt: "2026-09-01T08:30:00Z",
      authVersion: 1,
      platformRoles: []
    },
    {
      id: "u-002",
      email: "manager@vietnamshow.vn",
      name: "Trần Minh Thảo",
      emailVerified: true,
      emailVerifiedAt: "2026-08-15T10:00:00Z",
      authVersion: 2,
      platformRoles: [],
      memberships: [
        { orgId: "org-001", orgName: "Vietnam Show Corp", role: "MANAGER", active: true }
      ]
    },
    {
      id: "u-003",
      email: "checkin@vietnamshow.vn",
      name: "Lê Hoàng Phúc",
      emailVerified: true,
      emailVerifiedAt: "2026-08-20T14:15:00Z",
      authVersion: 1,
      platformRoles: [],
      memberships: [
        { orgId: "org-001", orgName: "Vietnam Show Corp", role: "CHECK_IN_STAFF", active: true }
      ]
    },
    {
      id: "u-000",
      email: "admin@ticketscenter.vn",
      name: "Quản trị viên Hệ thống",
      emailVerified: true,
      emailVerifiedAt: "2026-01-01T00:00:00Z",
      authVersion: 1,
      platformRoles: ["ADMIN"]
    }
  ],

  // Danh mục sự kiện
  categories: [
    { id: "all", name: "Tất cả", icon: "ph-squares-four" },
    { id: "music", name: "Nhạc sống", icon: "ph-music-notes" },
    { id: "stage", name: "Sân khấu & Nghệ thuật", icon: "ph-mask-happy" },
    { id: "museum", name: "Tham quan & Bảo tàng", icon: "ph-bank" },
    { id: "fanmeet", name: "Fan Meeting", icon: "ph-heart" },
    { id: "sports", name: "Thể thao", icon: "ph-soccer-ball" },
    { id: "conference", name: "Hội thảo & Triển lãm", icon: "ph-presentation" },
    { id: "nightlife", name: "Nightlife", icon: "ph-martini" }
  ],

  // 6.2 & 6.3 Tổ chức & Sự kiện
  organizations: [
    {
      id: "org-001",
      name: "Vietnam Show Corporation",
      contactEmail: "contact@vietnamshow.vn",
      phone: "0908 123 456",
      status: "ACTIVE",
      description: "Đơn vị tổ chức các đại nhạc hội và chương trình biểu diễn đỉnh cao tại Việt Nam.",
      commissionRule: {
        id: "cr-001",
        name: "Standard Standard Tier",
        ratePercent: 5.0,
        fixedFee: 5000,
        active: true
      }
    },
    {
      id: "org-002",
      name: "Bảo tàng Phụ nữ Việt Nam",
      contactEmail: "info@vwm.gov.vn",
      phone: "024 3825 9936",
      status: "ACTIVE",
      description: "Không gian trưng bày di sản, văn hóa và triển lãm lịch sử phụ nữ Việt Nam.",
      commissionRule: {
        id: "cr-002",
        name: "Di sản & Văn hóa",
        ratePercent: 3.0,
        fixedFee: 2000,
        active: true
      }
    }
  ],

  events: [
    {
      id: "evt-001",
      orgId: "org-002",
      orgName: "Bảo tàng Phụ nữ Việt Nam",
      categoryId: "museum",
      title: "Vé tham quan Bảo tàng Phụ nữ Việt Nam & Triển lãm Đặc biệt",
      tagline: "Khám phá chiều sâu văn hóa, di sản áo dài và hình tượng người phụ nữ Việt",
      coverImageUrl: "https://images.unsplash.com/photo-1554907984-15263bfd63bd?auto=format&fit=crop&w=1200&q=80",
      locationName: "Bảo tàng Phụ nữ Việt Nam",
      locationAddress: "36 Lý Thường Kiệt, Hàng Bài, Hoàn Kiếm, Hà Nội",
      city: "Hà Nội",
      status: "PUBLISHED", // DRAFT, PENDING_APPROVAL, REJECTED, PUBLISHED, CANCELLED
      saleStart: "2026-09-01T08:00:00",
      saleEnd: "2026-10-31T17:00:00",
      startTime: "2026-11-01T08:30:00",
      endTime: "2026-11-01T17:30:00",
      minPrice: 40000,
      zones: [
        {
          id: "z-01",
          name: "Vé Tiêu Chuẩn Người Lớn",
          type: "STANDING",
          price: 40000,
          capacity: 500,
          heldCount: 12,
          soldCount: 148
        },
        {
          id: "z-02",
          name: "Vé Trải Nghiệm Thuyết Minh & Audio Guide",
          type: "STANDING",
          price: 90000,
          capacity: 200,
          heldCount: 4,
          soldCount: 65
        }
      ],
      description: "Bảo tàng Phụ nữ Việt Nam là một trong những điểm đến văn hóa hàng đầu Thủ đô, lưu giữ hơn 40.000 hiện vật quý giá thể hiện vai trò của phụ nữ trong lịch sử dựng nước, kháng chiến và xây dựng đất nước."
    },
    {
      id: "evt-002",
      orgId: "org-001",
      orgName: "Vietnam Show Corporation",
      categoryId: "music",
      title: "Live Concert: Symphony of Autumn 2026",
      tagline: "Đêm hòa nhạc giao hưởng mùa thu quy tụ dàn nghệ sĩ thính phòng quốc tế",
      coverImageUrl: "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?auto=format&fit=crop&w=1200&q=80",
      locationName: "Nhà hát Lớn Hà Nội",
      locationAddress: "01 Tràng Tiền, Phan Chu Trinh, Hoàn Kiếm, Hà Nội",
      city: "Hà Nội",
      status: "PUBLISHED",
      saleStart: "2026-09-10T10:00:00",
      saleEnd: "2026-10-15T18:00:00",
      startTime: "2026-10-20T19:30:00",
      endTime: "2026-10-20T22:30:00",
      minPrice: 450000,
      zones: [
        {
          id: "z-vip",
          name: "Khu VIP - Tầng 1 Trung Tâm",
          type: "SEATED",
          price: 1200000,
          rows: ["A", "B"],
          seatsPerRow: 8,
          seats: [
            { id: "s-A1", label: "A-01", status: "AVAILABLE" },
            { id: "s-A2", label: "A-02", status: "AVAILABLE" },
            { id: "s-A3", label: "A-03", status: "SOLD" },
            { id: "s-A4", label: "A-04", status: "HELD" },
            { id: "s-A5", label: "A-05", status: "AVAILABLE" },
            { id: "s-A6", label: "A-06", status: "AVAILABLE" },
            { id: "s-A7", label: "A-07", status: "SOLD" },
            { id: "s-A8", label: "A-08", status: "AVAILABLE" },
            { id: "s-B1", label: "B-01", status: "AVAILABLE" },
            { id: "s-B2", label: "B-02", status: "AVAILABLE" },
            { id: "s-B3", label: "B-03", status: "AVAILABLE" },
            { id: "s-B4", label: "B-04", status: "AVAILABLE" },
            { id: "s-B5", label: "B-05", status: "SOLD" },
            { id: "s-B6", label: "B-06", status: "AVAILABLE" },
            { id: "s-B7", label: "B-07", status: "AVAILABLE" },
            { id: "s-B8", label: "B-08", status: "AVAILABLE" }
          ]
        },
        {
          id: "z-std",
          name: "Khu Tiêu Chuẩn - Tầng 2",
          type: "SEATED",
          price: 450000,
          rows: ["C", "D"],
          seatsPerRow: 8,
          seats: [
            { id: "s-C1", label: "C-01", status: "AVAILABLE" },
            { id: "s-C2", label: "C-02", status: "AVAILABLE" },
            { id: "s-C3", label: "C-03", status: "AVAILABLE" },
            { id: "s-C4", label: "C-04", status: "AVAILABLE" },
            { id: "s-C5", label: "C-05", status: "AVAILABLE" },
            { id: "s-C6", label: "C-06", status: "AVAILABLE" },
            { id: "s-C7", label: "C-07", status: "AVAILABLE" },
            { id: "s-C8", label: "C-08", status: "AVAILABLE" },
            { id: "s-D1", label: "D-01", status: "AVAILABLE" },
            { id: "s-D2", label: "D-02", status: "AVAILABLE" },
            { id: "s-D3", label: "D-03", status: "AVAILABLE" },
            { id: "s-D4", label: "D-04", status: "AVAILABLE" },
            { id: "s-D5", label: "D-05", status: "AVAILABLE" },
            { id: "s-D6", label: "D-06", status: "AVAILABLE" },
            { id: "s-D7", label: "D-07", status: "AVAILABLE" },
            { id: "s-D8", label: "D-08", status: "AVAILABLE" }
          ]
        }
      ],
      description: "Chương trình hòa nhạc mùa thu thường niên được chỉ huy bởi Nhạc trưởng khách mời hàng đầu châu Âu, biểu diễn những tuyệt phẩm của Tchaikovsky, Chopin và Dvořák."
    },
    {
      id: "evt-003",
      orgId: "org-001",
      orgName: "Vietnam Show Corporation",
      categoryId: "stage",
      title: "Vở Kịch: Hồn Trương Ba, Da Hàng Thịt (Phiên Bản Đương Đại)",
      tagline: "Kịch bản kinh điển của Lưu Quang Vũ tái xuất với ngôn ngữ sân khấu mới",
      coverImageUrl: "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?auto=format&fit=crop&w=1200&q=80",
      locationName: "Nhà hát Kịch Việt Nam",
      locationAddress: "01 Tràng Tiền, Hoàn Kiếm, Hà Nội",
      city: "Hà Nội",
      status: "PUBLISHED",
      saleStart: "2026-09-15T09:00:00",
      saleEnd: "2026-10-25T18:00:00",
      startTime: "2026-10-28T20:00:00",
      endTime: "2026-10-28T22:30:00",
      minPrice: 200000,
      zones: [
        { id: "z-k1", name: "Hàng Ghế Danh Dự", type: "SEATED", price: 500000, rows: ["A"], seatsPerRow: 6, seats: [] },
        { id: "z-k2", name: "Khán Phòng Phổ Thông", type: "SEATED", price: 200000, rows: ["B", "C"], seatsPerRow: 10, seats: [] }
      ]
    },
    {
      id: "evt-004",
      orgId: "org-001",
      orgName: "Vietnam Show Corporation",
      categoryId: "sports",
      title: "Trận Chung Kết Bóng Chuyền Quốc Gia 2026",
      tagline: "Màn so tài nảy lửa giữa hai đại diện xuất sắc nhất mùa giải",
      coverImageUrl: "https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=1200&q=80",
      locationName: "Cung Điền kinh Trong nhà Hà Nội",
      locationAddress: "Trần Hữu Dực, Mỹ Đình 1, Nam Từ Liêm, Hà Nội",
      city: "Hà Nội",
      status: "PUBLISHED",
      saleStart: "2026-09-20T08:00:00",
      saleEnd: "2026-11-05T15:00:00",
      startTime: "2026-11-06T18:30:00",
      endTime: "2026-11-06T21:30:00",
      minPrice: 150000,
      zones: [
        { id: "z-sp1", name: "Khán Đài A", type: "STANDING", price: 300000, capacity: 1000, heldCount: 50, soldCount: 450 },
        { id: "z-sp2", name: "Khán Đài B", type: "STANDING", price: 150000, capacity: 2000, heldCount: 80, soldCount: 890 }
      ]
    }
  ],

  // 6.5 Mã giảm giá (Coupon) - Capped <= 30% max
  coupons: [
    {
      id: "cp-001",
      orgId: "org-002",
      code: "DISCOVERVN10",
      discountType: "PERCENTAGE", // PERCENTAGE hoặc FIXED_AMOUNT
      discountValue: 10, // 10%
      maxUses: 200,
      reservedCount: 3,
      consumedCount: 42,
      active: true,
      validFrom: "2026-09-01T00:00:00",
      validUntil: "2026-11-30T23:59:59"
    },
    {
      id: "cp-002",
      orgId: "org-001",
      code: "AUTUMN50K",
      discountType: "FIXED_AMOUNT",
      discountValue: 50000, // Capped min(50000, 30% subtotal)
      maxUses: 100,
      reservedCount: 2,
      consumedCount: 38,
      active: true,
      validFrom: "2026-09-10T00:00:00",
      validUntil: "2026-10-31T23:59:59"
    },
    {
      id: "cp-003",
      orgId: "org-001",
      code: "VIPCONCERT25",
      discountType: "PERCENTAGE",
      discountValue: 25, // 25% (Hợp lệ < 30%)
      maxUses: 50,
      reservedCount: 1,
      consumedCount: 15,
      active: true,
      validFrom: "2026-09-10T00:00:00",
      validUntil: "2026-10-20T23:59:59"
    }
  ],

  // 6.4 Lượt giữ vé (TicketHold)
  currentHold: {
    id: "hld-90214",
    userId: "u-001",
    eventId: "evt-002",
    eventTitle: "Live Concert: Symphony of Autumn 2026",
    status: "ACTIVE", // ACTIVE, RELEASED, CONSUMED
    expiresAt: new Date(Date.now() + 8 * 60 * 1000 + 45 * 1000).toISOString(), // ~8m45s left
    items: [
      { id: "hi-01", zoneName: "Khu VIP - Tầng 1 Trung Tâm", seatLabel: "A-01", price: 1200000, quantity: 1 },
      { id: "hi-02", zoneName: "Khu VIP - Tầng 1 Trung Tâm", seatLabel: "A-02", price: 1200000, quantity: 1 }
    ]
  },

  // 6.6 Đơn hàng (Orders)
  orders: [
    {
      id: "ord-88391",
      userId: "u-001",
      eventId: "evt-001",
      eventTitle: "Vé tham quan Bảo tàng Phụ nữ Việt Nam & Triển lãm Đặc biệt",
      status: "PAID", // PENDING_PAYMENT, PAID, CANCELLED, EXPIRED
      createdAt: "2026-09-22T14:20:00Z",
      subtotalAmount: 180000,
      discountAmount: 18000,
      totalAmount: 162000,
      couponCode: "DISCOVERVN10",
      payment: {
        txnRef: "VNPAY-202609221420-88391",
        method: "VNPAY Sandbox",
        status: "CAPTURED",
        paidAt: "2026-09-22T14:23:45Z"
      },
      items: [
        { id: "oi-101", zoneName: "Vé Trải Nghiệm Thuyết Minh & Audio Guide", quantity: 2, unitPrice: 90000, totalPaid: 162000 }
      ],
      tickets: [
        {
          id: "tkt-001",
          ticketCode: "TC-VWM-99214-A",
          qrHash: "TICKET_VWM_001_8a9df2c4810e",
          zoneName: "Vé Trải Nghiệm Thuyết Minh",
          seatLabel: "Khu Đứng Tự Do",
          status: "ACTIVE", // ACTIVE, USED, REFUND_PENDING, REFUNDED, INVALIDATED
          paidAmount: 81000,
          usedAt: null
        },
        {
          id: "tkt-002",
          ticketCode: "TC-VWM-99215-B",
          qrHash: "TICKET_VWM_002_7b8ec1d3920f",
          zoneName: "Vé Trải Nghiệm Thuyết Minh",
          seatLabel: "Khu Đứng Tự Do",
          status: "ACTIVE",
          paidAmount: 81000,
          usedAt: null
        }
      ]
    },
    {
      id: "ord-77120",
      userId: "u-001",
      eventId: "evt-003",
      eventTitle: "Vở Kịch: Hồn Trương Ba, Da Hàng Thịt (Phiên Bản Đương Đại)",
      status: "PAID",
      createdAt: "2026-09-18T10:10:00Z",
      subtotalAmount: 400000,
      discountAmount: 0,
      totalAmount: 400000,
      couponCode: null,
      payment: {
        txnRef: "VNPAY-202609181010-77120",
        method: "VNPAY Sandbox",
        status: "CAPTURED",
        paidAt: "2026-09-18T10:12:10Z"
      },
      tickets: [
        {
          id: "tkt-003",
          ticketCode: "TC-STAGE-44810",
          qrHash: "TICKET_STAGE_003_110ae94d",
          zoneName: "Khán Phòng Phổ Thông",
          seatLabel: "B-05",
          status: "USED",
          paidAmount: 200000,
          usedAt: "2026-09-20T19:45:10Z"
        },
        {
          id: "tkt-004",
          ticketCode: "TC-STAGE-44811",
          qrHash: "TICKET_STAGE_004_221bf85e",
          zoneName: "Khán Phòng Phổ Thông",
          seatLabel: "B-06",
          status: "REFUND_PENDING",
          paidAmount: 200000,
          usedAt: null
        }
      ]
    }
  ],

  // 6.8 & 6.9 Yêu cầu hoàn vé (RefundRequest)
  refundRequests: [
    {
      id: "rr-001",
      orderId: "ord-77120",
      userEmail: "buyer@ticketscenter.vn",
      ticketIds: ["tkt-004"],
      totalRefundRequested: 200000,
      reasonType: "CUSTOMER_REQUEST", // CUSTOMER_REQUEST, EVENT_CANCELLATION
      customerReason: "Gặp sự cố cá nhân đột xuất nên không thể thu xếp đến xem đúng lịch diễn.",
      status: "PENDING", // PENDING, APPROVED, REJECTED, COMPLETED
      createdAt: "2026-09-23T09:15:00Z",
      reviewedAt: null,
      adminComment: null
    }
  ],

  // 6.2 Yêu cầu tạo tổ chức (OrganizationRequest)
  organizationRequests: [
    {
      id: "or-001",
      requesterEmail: "ngoclan@amusemusic.vn",
      requesterName: "Trương Ngọc Lan",
      orgName: "Amuse Music Production",
      phone: "0912 345 678",
      description: "Đơn vị sản xuất chuỗi minishow acoustic và hòa nhạc thính phòng độc lập.",
      status: "PENDING", // PENDING, APPROVED, REJECTED
      createdAt: "2026-09-23T11:00:00Z",
      rejectionReason: null
    },
    {
      id: "or-002",
      requesterEmail: "manager@vietnamshow.vn",
      requesterName: "Trần Minh Thảo",
      orgName: "Vietnam Show Corporation",
      phone: "0908 123 456",
      description: "Đơn vị tổ chức đại nhạc hội quốc tế.",
      status: "APPROVED",
      createdAt: "2026-08-15T08:00:00Z",
      rejectionReason: null
    }
  ],

  // 6.11 Đối soát & Quyết toán (Settlement)
  settlements: [
    {
      id: "stl-001",
      eventId: "evt-001",
      eventTitle: "Vé tham quan Bảo tàng Phụ nữ Việt Nam & Triển lãm Đặc biệt",
      orgName: "Bảo tàng Phụ nữ Việt Nam",
      grossAmount: 38500000, // Tổng thu sau giảm giá
      refundAmount: 1200000, // Hoàn thành công
      remainingAmount: 37300000,
      commissionRuleName: "Di sản & Văn hóa (3% + 2k/đơn)",
      commissionAmount: 1119000,
      netPayable: 36181000,
      status: "CONFIRMED", // DRAFT, CONFIRMED, PAID
      payouts: [
        {
          id: "po-001",
          amount: 36181000,
          payoutTxnRef: "PAYOUT-VWM-20260920-01",
          transferredAt: "2026-09-21T10:00:00Z",
          status: "SUCCEEDED"
        }
      ]
    }
  ],

  // 5.1 Nhật ký thao tác (AuditLog)
  auditLogs: [
    {
      id: "log-101",
      timestamp: "2026-09-24T09:30:15Z",
      actor: "admin@ticketscenter.vn",
      action: "APPROVE_ORGANIZATION_REQUEST",
      targetEntity: "OrganizationRequest #or-002",
      details: "Duyệt thành lập Vietnam Show Corp, gán CommissionRule cr-001 và cấp quyền MANAGER."
    },
    {
      id: "log-102",
      timestamp: "2026-09-24T08:15:00Z",
      actor: "buyer@ticketscenter.vn",
      action: "CREATE_REFUND_REQUEST",
      targetEntity: "Ticket #tkt-004",
      details: "Khách gửi yêu cầu hoàn 200,000đ cho vé B-06 (Hồn Trương Ba, Da Hàng Thịt)."
    },
    {
      id: "log-103",
      timestamp: "2026-09-23T17:45:22Z",
      actor: "checkin@vietnamshow.vn",
      action: "CHECK_IN_SUCCESS",
      targetEntity: "Ticket #tkt-003",
      details: "Check-in thành công qua quét camera QR tại Cửa A - Khán Phòng."
    },
    {
      id: "log-104",
      timestamp: "2026-09-22T14:23:45Z",
      actor: "SYSTEM",
      action: "VNPAY_IPN_CAPTURED",
      targetEntity: "Order #ord-88391",
      details: "Nhận IPN hợp lệ, phát hành 2 vé QR, tiêu thụ 1 lượt coupon DISCOVERVN10."
    }
  ]
};

// Expose globally
window.SEED_DATA = SEED_DATA;
