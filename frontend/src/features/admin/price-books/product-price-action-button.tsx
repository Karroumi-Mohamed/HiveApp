import type { ComponentProps } from "react";
import type { ProductPrice, ProductPriceAction } from "@/api/contracts";
import { useAdminSession } from "@/auth/session-provider";
import { Button } from "@/components/ui/button";
import { canUseProductPriceAction, productPriceActionReason } from "./product-price-rules";

type ProductPriceActionButtonProps = Omit<ComponentProps<typeof Button>, "disabled" | "title"> & {
  price: ProductPrice;
  action: ProductPriceAction;
};

/** Permission-aware button that preserves trigger event and ref props supplied through `asChild`. */
export function ProductPriceActionButton({
  price,
  action,
  children,
  variant = "outline",
  ...buttonProps
}: ProductPriceActionButtonProps) {
  const session = useAdminSession();
  const disabled = !canUseProductPriceAction(price, action, session.can);

  return (
    <Button
      {...buttonProps}
      aria-disabled={disabled || undefined}
      disabled={disabled}
      title={disabled ? productPriceActionReason(price, action, session.can) : undefined}
      variant={variant}
    >
      {children}
    </Button>
  );
}
