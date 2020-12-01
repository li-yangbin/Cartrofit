package com.liyangbin.cartrofit;

import com.liyangbin.cartrofit.annotation.Scope;

import java.lang.annotation.Annotation;

public abstract class CommandPropertyOperation<A extends Annotation> extends CommandImpl<A> {
    int propertyId;
    int area;
    CarPropertyDataSource source;

    @Override
    void copyFrom(CommandImpl owner) {
        super.copyFrom(owner);
        if (owner instanceof CommandPropertyOperation) {
            CommandPropertyOperation propertyOperation = (CommandPropertyOperation) owner;
            this.propertyId = propertyOperation.propertyId;
            this.area = propertyOperation.area;
        }
    }

    final void resolveArea(int userDeclaredArea) {
        if (userDeclaredArea != Scope.DEFAULT_AREA_ID) {
            this.area = userDeclaredArea;
        } else {
            if (this.record.apiArea != Scope.DEFAULT_AREA_ID) {
                this.area = record.apiArea;
            } else {
                this.area = Scope.GLOBAL_AREA_ID;
            }
        }
    }

    @Override
    String toCommandString() {
        String stable = "id:0x" + Integer.toHexString(getPropertyId())
                + (area != Scope.GLOBAL_AREA_ID ? " area:0x" + Integer.toHexString(area) : "");
        return stable + " " + super.toCommandString();
    }
}
