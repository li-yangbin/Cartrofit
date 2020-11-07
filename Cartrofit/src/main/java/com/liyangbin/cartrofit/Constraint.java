package com.liyangbin.cartrofit;

import java.util.Objects;

public class Constraint {
    static final Constraint ALL = new Constraint();
    int priority;
    int apiId;
    String category;
    CommandType type;

    private Constraint() {
    }

    public static Constraint of(int apiId) {
        Constraint constraint = new Constraint();
        if (apiId == 0) {
            throw new IllegalArgumentException("Invalid apiId:" + apiId);
        }
        constraint.apiId = apiId;
        return constraint;
    }

    public static Constraint of(String category) {
        Constraint constraint = new Constraint();
        constraint.category = Objects.requireNonNull(category);
        return constraint;
    }

    public static Constraint of(CommandType type) {
        Constraint constraint = new Constraint();
        constraint.type = Objects.requireNonNull(type);
        return constraint;
    }

    public Constraint and(int apiId) {
        if (apiId == 0) {
            throw new IllegalArgumentException("Invalid apiId:" + apiId);
        }
        this.apiId = apiId;
        return this;
    }

    public Constraint and(String category) {
        this.category = Objects.requireNonNull(category);
        return this;
    }

    public Constraint and(CommandType type) {
        this.type = Objects.requireNonNull(type);
        return this;
    }

    public Constraint priority(int priority) {
        this.priority = priority;
        return this;
    }

    boolean check(Command command) {
        if (this == ALL) {
            return true;
        }
        final CommandType thatType = command.getType();
        if (type == null && (thatType == CommandType.STICKY_GET
                || thatType == CommandType.RECEIVE)) {
            return false;
        }
        if (apiId != 0 && command.getId() != apiId) {
            return false;
        }
        if (type != null && type != thatType) {
            return false;
        }
        if (category != null) {
            String[] commandCategory = command.getCategory();
            if (commandCategory != null) {
                for (String category : commandCategory) {
                    if (this.category.equals(category)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Constraint that = (Constraint) o;

        if (apiId != that.apiId) return false;
        if (!Objects.equals(category, that.category))
            return false;
        return type == that.type;
    }

    @Override
    public int hashCode() {
        int result = apiId;
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Constraint{" +
                "apiId=" + apiId +
                ", category='" + category + '\'' +
                ", type=" + type +
                '}';
    }
}