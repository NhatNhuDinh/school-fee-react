import React from "react";
import { createPortal } from "react-dom";

import { PAYMENT_METHOD_LABEL, type Receipt } from "@/domains/fee-payment/types";
import { formatDate, formatNumber } from "@/shared/lib/format";

/**
 * Prints receipts. The receipts are drawn into a node on `document.body`, hidden on screen
 * and the only thing shown on paper while `body.printing-receipt` is set (see `styles/index.css`),
 * then `window.print()` is called. Nothing is opened in a popup, so a blocker cannot stop it.
 */
export function useReceiptPrinter() {
  const [receipts, setReceipts] = React.useState<Receipt[] | null>(null);

  React.useEffect(() => {
    if (!receipts || receipts.length === 0) return;
    const frame = requestAnimationFrame(() => {
      document.body.classList.add("printing-receipt");
      try {
        window.print();
      } finally {
        document.body.classList.remove("printing-receipt");
        setReceipts(null);
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [receipts]);

  const node =
    receipts && receipts.length > 0
      ? createPortal(
          <div className="receipt-print">
            {receipts.map((receipt) => (
              <ReceiptSheet key={receipt.id} receipt={receipt} />
            ))}
          </div>,
          document.body
        )
      : null;

  return { print: setReceipts, node };
}

function ReceiptSheet({ receipt }: { receipt: Receipt }) {
  return (
    <section className="receipt-sheet">
      <h1>PHIẾU THU TIỀN</h1>
      <p className="receipt-meta">
        Số: <b>{receipt.receiptCode}</b> · Ngày thu: <b>{formatDate(receipt.paidOn)}</b>
      </p>
      <dl>
        <div>
          <dt>Học sinh</dt>
          <dd>
            {receipt.studentName} ({receipt.studentCode})
          </dd>
        </div>
        <div>
          <dt>Lớp</dt>
          <dd>{receipt.className}</dd>
        </div>
        <div>
          <dt>Đợt thu</dt>
          <dd>{receipt.batchName}</dd>
        </div>
        <div>
          <dt>Hình thức</dt>
          <dd>{PAYMENT_METHOD_LABEL[receipt.method]}</dd>
        </div>
      </dl>
      <table>
        <thead>
          <tr>
            <th>Khoản thu</th>
            <th className="num">SL</th>
            <th className="num">Số tiền</th>
          </tr>
        </thead>
        <tbody>
          {receipt.lines.map((line, index) => (
            <tr key={index}>
              <td>{line.itemName}</td>
              <td className="num">{line.quantity}</td>
              <td className="num">{formatNumber(line.amount)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={2}>Tổng cộng</td>
            <td className="num">{formatNumber(receipt.amount)}</td>
          </tr>
        </tfoot>
      </table>
      <div className="receipt-sign">
        <span>Người nộp tiền</span>
        <span>Người thu tiền{receipt.collectorName ? `: ${receipt.collectorName}` : ""}</span>
      </div>
    </section>
  );
}
