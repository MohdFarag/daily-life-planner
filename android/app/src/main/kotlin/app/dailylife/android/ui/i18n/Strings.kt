package app.dailylife.android.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalLang = compositionLocalOf { "en" }

private val S: Map<String, Pair<String, String>> =
    mapOf(
        "otherPlace" to ("Another place" to "مكان آخر"),
        "latitude" to ("Latitude, e.g. 30.0444" to "خط العرض، مثل 30.0444"),
        "longitude" to ("Longitude, e.g. 31.2357" to "خط الطول، مثل 31.2357"),
        "timeZoneHint" to ("Time zone, e.g. Africa/Cairo" to "المنطقة الزمنية، مثل Africa/Cairo"),
        "useThisPlace" to ("Use this place" to "استخدم هذا المكان"),
        "invalidLocation" to
            ("Check the values: latitude −90 to 90, longitude −180 to 180, and a time zone like Europe/London." to
                "تحقق من القيم: خط العرض من −90 إلى 90، وخط الطول من −180 إلى 180، ومنطقة زمنية مثل Europe/London."),
        "blockMoveTitle" to
            ("%1 planned sessions move with this block" to "%1 جلسات مخططة تتحرك مع هذه الفترة"),
        "dragToPlan" to
            ("Long-press a task and drop it on the timeline to plan it" to
                "اضغط مطولًا على مهمة واسحبها إلى الجدول الزمني لتخطيطها"),
        "newEvent" to ("New event" to "حدث جديد"),
        "editEvent" to ("Edit event" to "تعديل الحدث"),
        "eventTitle" to ("What's happening?" to "ما الحدث؟"),
        "allDay" to ("All day" to "طوال اليوم"),
        "location" to ("Location" to "المكان"),
        "deleteEvent" to ("Delete event" to "حذف الحدث"),
        "calendar" to ("Calendar" to "التقويم"),
        "inBlock" to ("Part of a time block" to "ضمن فترة وقت"),
        "none" to ("None" to "بلا"),
        "noPrayerTimes" to
            ("Prayer times for that day aren't available; pick a fixed time." to
                "أوقات الصلاة لذلك اليوم غير متاحة؛ اختر وقتًا محددًا."),
        "subtasks" to ("Subtasks" to "المهام الفرعية"),
        "addSubtask" to ("Add a subtask" to "أضف مهمة فرعية"),
        "moveUp" to ("Move up" to "تحريك لأعلى"),
        "moveDown" to ("Move down" to "تحريك لأسفل"),
        "asSubtasks" to ("As subtasks of one task" to "كمهام فرعية لمهمة واحدة"),
        "context" to ("Context" to "السياق"),
        "anywhere" to ("Anywhere" to "في أي مكان"),
        "context_home" to ("At home" to "في المنزل"),
        "context_computer" to ("At the computer" to "على الحاسوب"),
        "context_phone" to ("Phone" to "الهاتف"),
        "context_errands" to ("Errands" to "مشاوير"),
        "context_outside" to ("Outside" to "في الخارج"),
        "inCalendar" to ("In Google Calendar" to "في تقويم Google"),
        "waitingForCalendar" to ("Waiting for Google Calendar" to "بانتظار تقويم Google"),
        "openInKeep" to ("Open in Keep" to "افتح في Keep"),
        "googleSection" to ("Google Tasks & Calendar" to "مهام Google والتقويم"),
        "googleExplain" to
            ("Sync tasks with Google Tasks, put work sessions in Google Calendar with a reminder, and see your appointments in the plan." to
                "زامن المهام مع مهام Google، وضع جلسات العمل في تقويم Google مع تذكير، واعرض مواعيدك في الخطة."),
        "connectGoogle" to ("Connect Google" to "ربط Google"),
        "googleNotConfigured" to
            ("Google isn't set up on this server yet." to "لم يُعدّ Google على هذا الخادم بعد."),
        "googleAccount" to ("Google account" to "حساب Google"),
        "googleReconnect" to
            ("Google access ended. Disconnect and connect again." to
                "انتهى الوصول إلى Google. افصل ثم اربط مجددًا."),
        "googleSyncProblem" to
            ("Last Google sync had a problem: %1" to "واجهت آخر مزامنة مع Google مشكلة: %1"),
        "calendars" to ("Calendars" to "التقويمات"),
        "calendarsSub" to ("Choose what shows in the plan" to "اختر ما يظهر في الخطة"),
        "disconnectGoogle" to ("Disconnect Google" to "فصل Google"),
        "googleConnected" to ("Google connected" to "تم ربط Google"),
        "googleDenied" to ("Google access was not granted" to "لم يُمنح الوصول إلى Google"),
        "googleFailed" to
            ("Connecting Google didn't work. Try again." to "تعذّر ربط Google. حاول مجددًا."),
        "googleNotConnected" to
            ("Connect Google from Account & sync first." to
                "اربط Google أولًا من الحساب والمزامنة."),
        "calendarsExplain" to
            ("Shown calendars appear in your plan. New events go to the default calendar." to
                "تظهر التقويمات المعروضة في خطتك. تُضاف الأحداث الجديدة إلى التقويم الافتراضي."),
        "appCalendars" to ("The app's calendars" to "تقويمات التطبيق"),
        "appCalendarSessions" to ("work sessions with reminders" to "جلسات العمل مع التذكيرات"),
        "appCalendarBlocks" to ("time blocks" to "فترات الوقت"),
        "showInPlan" to ("Show in plan" to "اعرض في الخطة"),
        "defaultCalendar" to ("Default for new events" to "الافتراضي للأحداث الجديدة"),
        "readOnlyCalendar" to ("Read only" to "للقراءة فقط"),
        "newCalendar" to ("New calendar" to "تقويم جديد"),
        "calendarName" to ("Calendar name" to "اسم التقويم"),
        "createCalendar" to ("Create calendar" to "إنشاء تقويم"),
        "assistantAccess" to ("Assistant access (MCP)" to "وصول المساعد (MCP)"),
        "assistantAccessExplain" to
            ("Let an AI assistant read your plan and add or schedule tasks. It gets its own token, which you can revoke any time; it never sees your password or Google account." to
                "اسمح لمساعد ذكي بقراءة خطتك وإضافة المهام وجدولتها. يحصل على رمز خاص به يمكنك إلغاؤه في أي وقت، ولا يرى كلمة مرورك أو حساب Google."),
        "assistantName" to ("Name, e.g. Claude on laptop" to "الاسم، مثل Claude على الحاسوب"),
        "createToken" to ("Create token" to "إنشاء رمز"),
        "tokenShownOnce" to
            ("Copy this token now; it won't be shown again" to
                "انسخ هذا الرمز الآن؛ لن يظهر مرة أخرى"),
        "mcpEndpoint" to ("Endpoint: %1" to "العنوان: %1"),
        "copyToken" to ("Copy token" to "نسخ الرمز"),
        "expiresOn" to ("Expires %1" to "ينتهي %1"),
        "revoke" to ("Revoke" to "إلغاء"),
        "account" to ("Account" to "الحساب"),
        "accountSync" to ("Account & sync" to "الحساب والمزامنة"),
        "syncIntro" to
            ("Sign in to keep your inbox, tasks and plans in sync across your devices. Everything keeps working offline." to
                "سجّل الدخول لمزامنة الوارد والمهام والخطط بين أجهزتك. كل شيء يعمل دون اتصال أيضًا."),
        "signIn" to ("Sign in" to "تسجيل الدخول"),
        "createAccount" to ("Create account" to "إنشاء حساب"),
        "serverAddress" to ("Server address" to "عنوان الخادم"),
        "email" to ("Email" to "البريد الإلكتروني"),
        "password" to ("Password" to "كلمة المرور"),
        "passwordHint" to ("At least 10 characters" to "10 أحرف على الأقل"),
        "dataStaysNote" to
            ("Data already on this phone stays here and is uploaded after you sign in." to
                "تبقى البيانات الموجودة على هذا الهاتف وتُرفع بعد تسجيل الدخول."),
        "syncState" to ("Sync" to "المزامنة"),
        "syncNow" to ("Sync now" to "زامن الآن"),
        "syncing" to ("Syncing…" to "جارٍ المزامنة…"),
        "lastSynced" to ("Synced %1" to "تمت المزامنة %1"),
        "notSyncedYet" to ("Not synced yet" to "لم تتم المزامنة بعد"),
        "syncOffline" to
            ("Offline — will sync when connected" to "غير متصل — ستتم المزامنة عند الاتصال"),
        "syncFailed" to ("Last sync failed; it will retry" to "فشلت آخر مزامنة وستُعاد المحاولة"),
        "signedOutError" to
            ("Your session ended. Sign in again." to "انتهت جلستك. سجّل الدخول مجددًا."),
        "pendingSync" to ("Waiting to sync" to "بانتظار المزامنة"),
        "allSynced" to ("All synced" to "كل شيء متزامن"),
        "needsAttention" to ("Needs your attention" to "يحتاج إلى انتباهك"),
        "waitingToSync" to ("%1 waiting to sync" to "%1 بانتظار المزامنة"),
        "discardChange" to ("Discard" to "تجاهل"),
        "deletedElsewhere" to ("Deleted on another device" to "حُذف على جهاز آخر"),
        "serverRejected" to ("Not accepted by the server: %1" to "لم يقبله الخادم: %1"),
        "signOut" to ("Sign out" to "تسجيل الخروج"),
        "signedIn" to ("Signed in" to "تم تسجيل الدخول"),
        "deleteAccount" to ("Delete account" to "حذف الحساب"),
        "deleteAccountExplain" to
            ("This deletes your account and everything synced to the server, including uploaded files. Data on this phone stays until you remove the app. Nothing in Google is changed." to
                "يحذف هذا حسابك وكل ما تمت مزامنته مع الخادم، بما في ذلك الملفات المرفوعة. تبقى البيانات على هذا الهاتف حتى تحذف التطبيق. لا يتغير شيء في Google."),
        "deleteAccountConfirm" to ("Delete my account" to "احذف حسابي"),
        "accountDeleted" to ("Account deleted" to "تم حذف الحساب"),
        "passwordTooShort" to
            ("Use a password of at least 10 characters" to "استخدم كلمة مرور من 10 أحرف على الأقل"),
        "wrongCredentials" to
            ("Email or password is incorrect" to "البريد الإلكتروني أو كلمة المرور غير صحيحة"),
        "emailTaken" to
            ("An account with this email already exists" to "يوجد حساب بهذا البريد الإلكتروني"),
        "wrongPassword" to ("Password is incorrect" to "كلمة المرور غير صحيحة"),
        "tooManyAttempts" to
            ("Too many attempts; try again in a minute" to "محاولات كثيرة؛ حاول بعد دقيقة"),
        "serverUnreachable" to
            ("Can't reach the server. Check the address and your connection." to
                "تعذّر الوصول إلى الخادم. تحقق من العنوان والاتصال."),
        "invalidServer" to
            ("Enter a valid https:// server address" to "أدخل عنوان خادم صالحًا يبدأ بـ https://"),
        "serverError" to ("The server returned an error" to "أعاد الخادم خطأ"),
        "today" to ("Today" to "اليوم"),
        "inbox" to ("Inbox" to "الوارد"),
        "plan" to ("Plan" to "الخطة"),
        "more" to ("More" to "المزيد"),
        "search" to ("Search" to "بحث"),
        "back" to ("Back" to "رجوع"),
        "close" to ("Close" to "إغلاق"),
        "save" to ("Save" to "حفظ"),
        "cancel" to ("Cancel" to "إلغاء"),
        "delete" to ("Delete" to "حذف"),
        "undo" to ("Undo" to "تراجع"),
        "tasksComplete" to ("%1 of %2 tasks complete" to "%1 من %2 مهام مكتملة"),
        "upNext" to ("UP NEXT" to "التالي"),
        "startSession" to ("Start session" to "ابدأ الجلسة"),
        "resume" to ("Resume" to "استئناف"),
        "pause" to ("Pause" to "إيقاف مؤقت"),
        "yourDay" to ("Your day" to "خطة اليوم"),
        "viewPlan" to ("View plan" to "عرض الخطة"),
        "protectedTime" to ("Protected time" to "وقت محمي"),
        "prayerOf" to ("%1 prayer" to "صلاة %1"),
        "leftUnplanned" to ("%1 left for unplanned work" to "%1 متبقية للأعمال غير المخططة"),
        "dayFull" to ("Your day is fully planned" to "يومك مخطط بالكامل"),
        "nothingNext" to ("Nothing scheduled next" to "لا شيء مجدول بعد"),
        "nothingNextHint" to
            ("Plan a task or capture something new." to "خطط لمهمة أو التقط فكرة جديدة."),
        "openPlan" to ("Open plan" to "افتح الخطة"),
        "setCity" to ("Set your city for accurate prayer times" to "حدد مدينتك لمواقيت صلاة دقيقة"),
        "needsReview" to ("Needs review" to "تحتاج مراجعة"),
        "followUpsDue" to ("%1 follow-ups due" to "%1 متابعات مستحقة"),
        "min" to ("min" to "دقيقة"),
        "work" to ("Work" to "العمل"),
        "personal" to ("Personal" to "شخصي"),
        "family" to ("Family" to "العائلة"),
        "blockAdvice" to ("%1 is meant for other lists" to "فترة %1 مخصصة لقوائم أخرى"),
        "minutes" to ("%1 minutes" to "%1 دقيقة"),
        "inboxSubtitle" to ("%1 items, ready when you are" to "%1 عناصر، جاهزة متى شئت"),
        "all" to ("All" to "الكل"),
        "notes" to ("Notes" to "ملاحظات"),
        "links" to ("Links" to "روابط"),
        "media" to ("Media" to "وسائط"),
        "processInbox" to ("Process inbox" to "معالجة الوارد"),
        "inboxEmpty" to ("Inbox is clear" to "الوارد فارغ"),
        "inboxEmptyHint" to
            ("Everything has a place. Capture anything with +." to
                "لكل شيء مكانه. التقط أي شيء بزر +."),
        "savedOnDevice" to
            ("Everything is saved on this device." to "كل شيء محفوظ على هذا الجهاز."),
        "localOnly" to
            ("On this device only — sign in to sync" to
                "على هذا الجهاز فقط — سجّل الدخول للمزامنة"),
        "captureSomething" to ("Capture something" to "التقط فكرة"),
        "captureSubtitle" to
            ("Save now. Organize when you are ready." to "احفظ الآن ونظّم لاحقًا."),
        "text" to ("Text" to "نص"),
        "voice" to ("Voice" to "صوت"),
        "photo" to ("Photo" to "صورة"),
        "video" to ("Video" to "فيديو"),
        "file" to ("File" to "ملف"),
        "list" to ("List" to "قائمة"),
        "capturePlaceholder" to
            ("What's on your mind? Paste a link or jot an idea…" to
                "ما الذي يدور في ذهنك؟ الصق رابطًا أو دوّن فكرة…"),
        "listPlaceholder" to ("One entry per line" to "عنصر في كل سطر"),
        "worksOffline" to
            ("Works offline. No project or date needed." to
                "يعمل دون اتصال. لا حاجة لمشروع أو تاريخ."),
        "saveToInbox" to ("Save to Inbox" to "احفظ في الوارد"),
        "savedToInbox" to ("Saved to Inbox" to "تم الحفظ في الوارد"),
        "captureFooter" to
            ("Text, links, notes — one place for everything." to
                "نصوص وروابط وملاحظات — مكان واحد لكل شيء."),
        "record" to ("Start recording" to "ابدأ التسجيل"),
        "stopRecord" to ("Stop recording" to "أوقف التسجيل"),
        "recording" to ("Recording… %1" to "جارٍ التسجيل… %1"),
        "takePhoto" to ("Take photo" to "التقط صورة"),
        "recordVideo" to ("Record video" to "سجّل فيديو"),
        "chooseFile" to ("Choose files" to "اختر ملفات"),
        "attachments" to ("%1 attachments" to "%1 مرفقات"),
        "caption" to ("Add a caption (optional)" to "أضف وصفًا (اختياري)"),
        "duplicateShare" to ("This was already captured" to "تم التقاط هذا من قبل"),
        "openExisting" to ("Open existing" to "افتح الموجود"),
        "keepNew" to ("Keep a new copy" to "احتفظ بنسخة جديدة"),
        "sharedFrom" to ("Shared from %1" to "مُشارك من %1"),
        "itemOf" to ("ITEM %1 OF %2" to "العنصر %1 من %2"),
        "whatBecome" to ("What should this become?" to "ماذا يجب أن يصبح هذا؟"),
        "createTask" to ("Create a task" to "أنشئ مهمة"),
        "doNow" to ("Do now" to "نفّذ الآن"),
        "saveReference" to ("Save as reference" to "احفظ كمرجع"),
        "otherDecisions" to ("Someday, waiting, split or trash" to "لاحقًا، انتظار، تقسيم أو حذف"),
        "someday" to ("Someday" to "يومًا ما"),
        "waitingFor" to ("Waiting for" to "بانتظار"),
        "split" to ("Split" to "تقسيم"),
        "trash" to ("Trash" to "المهملات"),
        "moveToTrash" to ("Move to trash" to "انقل إلى المهملات"),
        "processEntries" to ("Process entries one by one" to "عالج العناصر واحدًا تلو الآخر"),
        "toTasks" to ("Tasks" to "مهام"),
        "toReference" to ("Reference" to "مرجع"),
        "applyEntries" to ("Apply" to "طبّق"),
        "convertToList" to ("Turn into list entries" to "حوّل إلى عناصر قائمة"),
        "doNowRunning" to ("Doing it now" to "قيد التنفيذ الآن"),
        "doNowHint" to
            ("Mark done when finished. Nothing completes on its own." to
                "علّمها كمنجزة عند الانتهاء. لا يكتمل شيء تلقائيًا."),
        "markDone" to ("Mark done" to "علّم كمنجز"),
        "doNowToTask" to ("Turn into a task instead" to "حوّلها إلى مهمة بدلًا من ذلك"),
        "who" to ("Who or what are you waiting for?" to "من أو ماذا تنتظر؟"),
        "followUpOn" to ("Follow up on" to "تابع في"),
        "reviewOn" to ("Review on (optional)" to "راجع في (اختياري)"),
        "splitHint" to
            ("One part per line. Each becomes its own inbox item." to
                "جزء في كل سطر. يصبح كل جزء عنصرًا مستقلًا في الوارد."),
        "inboxZero" to ("Inbox processed" to "تمت معالجة الوارد"),
        "inboxZeroHint" to ("Every item has a place now." to "لكل عنصر مكانه الآن."),
        "backToInbox" to ("Back to Inbox" to "العودة إلى الوارد"),
        "movedTo" to ("Moved to %1" to "نُقل إلى %1"),
        "capturedAgo" to ("Captured %1 · Original kept" to "التُقط %1 · الأصل محفوظ"),
        "task" to ("Task" to "مهمة"),
        "newTask" to ("New task" to "مهمة جديدة"),
        "important" to ("Important" to "مهم"),
        "markImportant" to ("Mark important" to "علّمها كمهمة"),
        "inProgress" to ("In progress" to "قيد التنفيذ"),
        "listLabel" to ("List" to "القائمة"),
        "estimate" to ("Estimate" to "التقدير"),
        "deadline" to ("Deadline" to "الموعد النهائي"),
        "target" to ("My completion target" to "هدفي للإنجاز"),
        "tags" to ("Tags" to "الوسوم"),
        "notSet" to ("Not set" to "غير محدد"),
        "originalNote" to ("Original capture" to "الالتقاط الأصلي"),
        "openSource" to ("Open source and attachments" to "افتح المصدر والمرفقات"),
        "sessions" to ("Sessions" to "الجلسات"),
        "readyWhenYouAre" to ("Ready when you are" to "جاهزة متى شئت"),
        "noTimeSet" to
            ("No time set. Add a session to your plan." to "لا يوجد وقت محدد. أضف جلسة إلى خطتك."),
        "startFocus" to ("Start focus" to "ابدأ التركيز"),
        "planSession" to ("Plan a session" to "خطط لجلسة"),
        "addSession" to ("Add session" to "أضف جلسة"),
        "continueProcessing" to ("Continue processing" to "تابع المعالجة"),
        "deleteTask" to ("Delete task" to "احذف المهمة"),
        "completed" to ("Completed" to "مكتملة"),
        "reopen" to ("Reopen" to "أعد فتحها"),
        "complete" to ("Complete task" to "إكمال المهمة"),
        "notesHint" to ("Add a short description" to "أضف وصفًا قصيرًا"),
        "every" to ("Repeats" to "يتكرر"),
        "pinned" to ("Fixed for this day" to "ثابتة لهذا اليوم"),
        "day" to ("Day" to "يوم"),
        "week" to ("Week" to "أسبوع"),
        "agenda" to ("Agenda" to "جدول"),
        "tasks" to ("Tasks" to "المهام"),
        "prayers" to ("Prayers" to "الصلوات"),
        "events" to ("Events" to "مواعيد"),
        "notPlanned" to ("Not yet planned" to "لم تُخطط بعد"),
        "emptyDay" to ("Nothing planned for this day." to "لا شيء مخطط لهذا اليوم."),
        "fixedTime" to ("Fixed time" to "وقت محدد"),
        "relativeToPrayer" to ("Relative to prayer" to "مرتبط بالصلاة"),
        "date" to ("Date" to "التاريخ"),
        "startTime" to ("Start time" to "وقت البدء"),
        "duration" to ("Duration" to "المدة"),
        "startsAfter" to ("Starts after" to "يبدأ بعد"),
        "startsBefore" to ("Starts before" to "يبدأ قبل"),
        "endsBefore" to ("Ends before" to "ينتهي قبل"),
        "adhan" to ("Adhan" to "الأذان"),
        "iqama" to ("Iqama" to "الإقامة"),
        "periodEnd" to ("End of prayer" to "نهاية الصلاة"),
        "offset" to ("Offset" to "الفارق"),
        "repeatDaily" to ("Repeat every day" to "كرر يوميًا"),
        "fitsBlock" to ("Fits your %1 block." to "يناسب فترة %1."),
        "free" to ("This time is free." to "هذا الوقت متاح."),
        "conflict" to ("Overlaps %1." to "يتعارض مع %1."),
        "suggestions" to ("Free times" to "أوقات متاحة"),
        "apply" to ("Apply schedule" to "طبّق الجدولة"),
        "applyAnyway" to ("Schedule anyway" to "جدول على أي حال"),
        "multiSession" to
            ("A task can have more than one session." to "يمكن أن يكون للمهمة أكثر من جلسة."),
        "focusSession" to ("FOCUS SESSION" to "جلسة تركيز"),
        "sessionPaused" to ("SESSION PAUSED" to "الجلسة متوقفة مؤقتًا"),
        "onBreak" to ("ON A BREAK" to "استراحة"),
        "remainingOf" to ("remaining of %1 minutes" to "متبقية من %1 دقيقة"),
        "overtime" to ("Overtime · still counts as work" to "وقت إضافي · يُحتسب عملًا"),
        "pausedNote" to ("Paused · Time is not counting" to "متوقفة · الوقت لا يُحتسب"),
        "breakNote" to ("Break time is kept separate" to "وقت الاستراحة يُسجل منفصلًا"),
        "endsAt" to ("Ends at %1" to "تنتهي عند %1"),
        "takeBreak" to ("Take a break" to "خذ استراحة"),
        "endSession" to ("End session" to "إنهاء الجلسة"),
        "endingKeepsOpen" to
            ("Ending a session keeps the task open." to "إنهاء الجلسة يُبقي المهمة مفتوحة."),
        "addFive" to ("Add 5 minutes" to "أضف ٥ دقائق"),
        "noFocus" to ("No session running" to "لا توجد جلسة جارية"),
        "moreSubtitle" to ("Your plans, notes, and preferences" to "خططك وملاحظاتك وتفضيلاتك"),
        "yourSpace" to ("YOUR SPACE" to "مساحتك"),
        "settings" to ("SETTINGS" to "الإعدادات"),
        "allTasks" to ("All tasks" to "كل المهام"),
        "openCount" to ("%1 open" to "%1 مفتوحة"),
        "collections" to ("Collections" to "المجموعات"),
        "collectionsSub" to ("References, someday and waiting" to "مراجع، يومًا ما، وانتظار"),
        "prayerTimes" to ("Prayer times" to "مواقيت الصلاة"),
        "timeBlocks" to ("Time blocks" to "فترات الوقت"),
        "timeBlocksSub" to ("Work, family and other windows" to "العمل والعائلة وغيرها"),
        "lists" to ("Lists" to "القوائم"),
        "preferences" to ("Preferences" to "التفضيلات"),
        "preferencesSub" to ("Language, reminders and data" to "اللغة والتذكيرات والبيانات"),
        "exportData" to ("Export data" to "تصدير البيانات"),
        "emptyTrash" to ("Empty trash" to "أفرغ المهملات"),
        "restore" to ("Restore" to "استعادة"),
        "deleteForever" to ("Delete forever" to "احذف نهائيًا"),
        "purgeOn" to ("Deleted permanently on %1" to "يُحذف نهائيًا في %1"),
        "trashEmpty" to ("Trash is empty." to "المهملات فارغة."),
        "returnToInbox" to ("Return to Inbox" to "أعد إلى الوارد"),
        "references" to ("References" to "مراجع"),
        "emptyCollection" to ("Nothing saved here yet." to "لا شيء محفوظ هنا بعد."),
        "city" to ("City" to "المدينة"),
        "method" to ("Calculation method" to "طريقة الحساب"),
        "asrMethod" to ("Asr (Hanafi)" to "العصر (حنفي)"),
        "iqamaAfter" to ("Iqama after adhan (min)" to "الإقامة بعد الأذان (دقيقة)"),
        "endAfter" to ("Prayer ends after iqama (min)" to "تنتهي الصلاة بعد الإقامة (دقيقة)"),
        "adjust" to ("Adjust (min)" to "تعديل (دقيقة)"),
        "fridayExtra" to ("Extra time on Friday (min)" to "وقت إضافي يوم الجمعة (دقيقة)"),
        "protectPrayers" to ("Protect prayer times" to "حماية أوقات الصلاة"),
        "prayerSettingsInvalid" to
            ("These settings do not produce valid prayer times for today." to
                "هذه الإعدادات لا تعطي مواقيت صلاة صحيحة لليوم."),
        "methodNote" to
            ("Calculated on this device. Times may differ from your mosque timetable." to
                "تُحسب على هذا الجهاز. قد تختلف عن جدول مسجدك."),
        "guiding" to ("Guiding" to "إرشادية"),
        "protected" to ("Protected" to "محمية"),
        "blockName" to ("Name" to "الاسم"),
        "from" to ("From" to "من"),
        "to" to ("To" to "إلى"),
        "allowedLists" to ("Allowed lists" to "القوائم المسموحة"),
        "addBlock" to ("Add block" to "أضف فترة"),
        "addList" to ("Add list" to "أضف قائمة"),
        "newList" to ("New list name" to "اسم القائمة الجديدة"),
        "language" to ("Language" to "اللغة"),
        "reminderBefore" to ("Remind me before a session (min)" to "ذكّرني قبل الجلسة (دقيقة)"),
        "searchHint" to ("Search tasks and notes" to "ابحث في المهام والملاحظات"),
        "noResults" to ("No matches." to "لا نتائج."),
        "justNow" to ("just now" to "الآن"),
        "minAgo" to ("%1 min ago" to "قبل %1 دقيقة"),
        "hoursAgo" to ("%1 h ago" to "قبل %1 ساعة"),
        "daysAgo" to ("%1 days ago" to "قبل %1 أيام"),
        "noteKind" to ("Quick note" to "ملاحظة سريعة"),
        "voiceKind" to ("Voice note" to "ملاحظة صوتية"),
        "linkKind" to ("Link" to "رابط"),
        "listKind" to ("List" to "قائمة"),
        "imageKind" to ("Photo" to "صورة"),
        "videoKind" to ("Video" to "فيديو"),
        "fileKind" to ("File" to "ملف"),
        "notifyPermission" to
            ("Allow notifications for session reminders" to "اسمح بالإشعارات لتذكيرات الجلسات"),
        "micDenied" to
            ("Microphone permission is needed to record." to "يلزم إذن الميكروفون للتسجيل."),
    )

private const val AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"

fun localizeDigits(s: String, lang: String) =
    if (lang == "ar") s.map { if (it in '0'..'9') AR_DIGITS[it - '0'] else it }.joinToString("")
    else s

fun translate(lang: String, key: String, vararg args: Any): String {
    val pair = S[key] ?: return key
    var s = if (lang == "ar") pair.second else pair.first
    args.forEachIndexed { i, a -> s = s.replace("%${i + 1}", a.toString()) }
    return localizeDigits(s, lang)
}

@Composable fun tr(key: String, vararg args: Any): String = translate(LocalLang.current, key, *args)

/**
 * Digits plus a left-to-right isolate for times so "10:00–10:45" never flips inside Arabic text.
 */
@Composable
fun num(s: Any): String {
    val lang = LocalLang.current
    val out = localizeDigits(s.toString(), lang)
    return if (lang == "ar" && ':' in out) "\u2066$out\u2069" else out
}
