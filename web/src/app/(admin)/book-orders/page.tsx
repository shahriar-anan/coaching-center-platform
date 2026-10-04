"use client";

import { useCallback, useEffect, useState } from "react";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { AdminBookOrderView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export default function BookOrdersPage() {
  const { t } = useLocale();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const [rows, setRows] = useState<AdminBookOrderView[]>([]);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await api.listBookOrders(0);
      setRows(data.content);
    } catch (err) {
      setError(err);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side list fetch
    void load();
  }, [load]);

  async function advance(order: AdminBookOrderView, status: string) {
    await api.updateBookOrderStatus(order.orderId, status);
    await load();
  }

  function actions(order: AdminBookOrderView) {
    const buttons: { label: string; status: string }[] = [];
    if (order.paymentMethod === "ONLINE") {
      if (order.status === "PAID") {
        buttons.push({ label: t("orders.advanceReady"), status: "READY" });
      }
      if (order.status === "READY") {
        buttons.push({
          label: order.fulfillment === "DELIVERY" ? t("orders.markDelivered") : t("orders.markCollected"),
          status: order.fulfillment === "DELIVERY" ? "DELIVERED" : "COLLECTED",
        });
      }
    } else {
      if (order.status === "PLACED") {
        buttons.push({ label: t("orders.advanceReady"), status: "READY" });
      }
      if (order.status === "READY" && isMaster) {
        buttons.push({ label: t("orders.recordCash"), status: "COLLECTED" });
      }
    }
    return buttons;
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold">{t("orders.title")}</h1>
      <FormError error={error} />
      {rows.length === 0 ? (
        <p className="mt-8 text-zinc-600">{t("orders.empty")}</p>
      ) : (
        <table className="mt-6 w-full text-sm">
          <thead>
            <tr className="border-b text-left">
              <th className="py-2">{t("orders.status")}</th>
              <th className="py-2">{t("orders.fulfillment")}</th>
              <th className="py-2">{t("orders.paymentMethod")}</th>
              <th className="py-2">{t("orders.phone")}</th>
              <th className="py-2">{t("orders.address")}</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((order) => (
              <tr key={order.orderId} className="border-b border-zinc-100 align-top">
                <td className="py-2">{order.status}</td>
                <td className="py-2">{order.fulfillment}</td>
                <td className="py-2">{order.paymentMethod}</td>
                <td className="py-2">{order.phone}</td>
                <td className="py-2">
                  {order.address ?? "—"}
                  {order.area ? ` (${order.area})` : ""}
                </td>
                <td className="py-2 space-x-2">
                  {actions(order).map((action) => (
                    <button
                      key={action.status}
                      type="button"
                      className="rounded border px-2 py-1"
                      onClick={() => advance(order, action.status)}
                    >
                      {action.label}
                    </button>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
