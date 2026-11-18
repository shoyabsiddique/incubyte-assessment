package com.acme.salary.employees.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The single row that remembers the last Employee ID number handed out. */
@Entity
@Table(name = "employee_id_sequence")
public class EmployeeIdSequenceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Byte id;

    @Column(name = "last_value", nullable = false)
    private int lastValue;

    protected EmployeeIdSequenceEntity() {}

    public int getLastValue() {
        return lastValue;
    }

    public void advanceTo(int value) {
        this.lastValue = value;
    }
}
