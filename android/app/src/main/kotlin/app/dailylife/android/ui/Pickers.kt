package app.dailylife.android.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

fun pickDate(context: Context, initial: LocalDate, min: LocalDate? = null, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
        if (min != null) datePicker.minDate = min.toEpochDay() * 86_400_000L
    }.show()
}

fun pickTime(context: Context, initial: LocalTime, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPick(LocalTime.of(h, m)) }, initial.hour, initial.minute, true).show()
}
