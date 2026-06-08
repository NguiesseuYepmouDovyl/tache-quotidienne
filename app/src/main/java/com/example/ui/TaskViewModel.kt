package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TaskRepository

    init {
        val taskDao = AppDatabase.getDatabase(application).taskDao()
        repository = TaskRepository(taskDao)
    }

    // Raw sources from repository and user UI actions
    val allTasks: Flow<List<Task>> = repository.allTasks

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>("Tous")
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedPriority = MutableStateFlow<String?>("Tous")
    val selectedPriority: StateFlow<String?> = _selectedPriority.asStateFlow()

    // Dialog state for Add/Edit
    private val _showAddEditDialog = MutableStateFlow(false)
    val showAddEditDialog: StateFlow<Boolean> = _showAddEditDialog.asStateFlow()

    private val _taskToEdit = MutableStateFlow<Task?>(null)
    val taskToEdit: StateFlow<Task?> = _taskToEdit.asStateFlow()

    // Combining filters into reactive filtered tasks
    val filteredTasks: StateFlow<List<Task>> = combine(
        allTasks,
        _searchQuery,
        _selectedCategory,
        _selectedPriority
    ) { tasks, query, category, priority ->
        tasks.filter { task ->
            val matchQuery = query.isEmpty() || 
                    task.title.contains(query, ignoreCase = true) || 
                    task.description.contains(query, ignoreCase = true)
            
            val matchCategory = category == "Tous" || task.category == category
            val matchPriority = priority == "Tous" || task.priority == priority

            matchQuery && matchCategory && matchPriority
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Statistics Flow
    val statsFlow: StateFlow<TaskStats> = allTasks.map { tasks ->
        val total = tasks.size
        val completed = tasks.count { it.isCompleted }
        val pending = total - completed
        val percentage = if (total > 0) (completed.toFloat() / total * 100).toInt() else 0
        
        // Custom motivation messages based on progress
        val encouragement = when {
            total == 0 -> "Ajoutez votre première tâche pour commencer !"
            percentage == 100 -> "Félicitations ! Toutes les tâches sont complétées ! 🎉"
            percentage >= 75 -> "Presque fini ! Continuez votre excellent travail ! 💪"
            percentage >= 50 -> "À mi-chemin ! Vous avancez bien ! ✨"
            percentage >= 25 -> "Bon début, continuez comme ça ! 👍"
            else -> "Une étape à la fois. Prêt pour aujourd'hui ? 😊"
        }

        TaskStats(
            totalCount = total,
            completedCount = completed,
            pendingCount = pending,
            completionPercentage = percentage,
            message = encouragement
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskStats())

    // Actions
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category ?: "Tous"
    }

    fun selectPriority(priority: String?) {
        _selectedPriority.value = priority ?: "Tous"
    }

    fun openAddTaskDialog() {
        _taskToEdit.value = null
        _showAddEditDialog.value = true
    }

    fun openEditTaskDialog(task: Task) {
        _taskToEdit.value = task
        _showAddEditDialog.value = true
    }

    fun closeAddEditDialog() {
        _showAddEditDialog.value = false
        _taskToEdit.value = null
    }

    fun saveTask(title: String, description: String, priority: String, category: String, dueDate: Long? = null) {
        viewModelScope.launch {
            val currentEdit = _taskToEdit.value
            if (currentEdit != null) {
                // Update
                val updatedTask = currentEdit.copy(
                    title = title,
                    description = description,
                    priority = priority,
                    category = category,
                    dueDate = dueDate
                )
                repository.update(updatedTask)
            } else {
                // Insert new
                val newTask = Task(
                    title = title,
                    description = description,
                    priority = priority,
                    category = category,
                    dueDate = dueDate
                )
                repository.insert(newTask)
            }
            closeAddEditDialog()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.updateCompletion(task.id, !task.isCompleted)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.delete(task)
        }
    }

    fun clearAllTasks() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    // Insert dummy tasks to help user start with sample content
    fun insertSampleTasks() {
        viewModelScope.launch {
            repository.insert(Task(title = "Acheter des fruits frais", description = "Pommes, bananes et oranges de saison", priority = "LOW", category = "Courses"))
            repository.insert(Task(title = "Préparer la réunion trimestrielle", description = "Finaliser les diapositives et le script de présentation", priority = "HIGH", category = "Travail"))
            repository.insert(Task(title = "30 mins de course à pied", description = "Courir dans le parc voisin pour s'oxygéner", priority = "MEDIUM", category = "Santé"))
            repository.insert(Task(title = "Lire un chapitre de livre", description = "Continuer la lecture de 'Clean Code'", priority = "LOW", category = "Loisirs"))
        }
    }
}

data class TaskStats(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val pendingCount: Int = 0,
    val completionPercentage: Int = 0,
    val message: String = "Ajoutez votre première tâche pour commencer !"
)
