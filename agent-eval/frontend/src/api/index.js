import http from './http'

export const getTasks = () => http.get('/tasks')
export const getTask = (id) => http.get(`/tasks/${id}`)
export const createTask = (data) => http.post('/tasks', data)
export const updateTask = (id, data) => http.put(`/tasks/${id}`, data)
export const deleteTask = (id) => http.delete(`/tasks/${id}`)
export const runTask = (id) => http.post(`/tasks/${id}/run`)
export const getTaskRecords = (id) => http.get(`/tasks/${id}/records`)
export const compareTasks = (ids) => http.get(`/tasks/compare/summary?task_ids=${ids.join(',')}`)

export const getDatasets = () => http.get('/datasets')
export const getDataset = (id) => http.get(`/datasets/${id}`)
export const createDataset = (data) => http.post('/datasets', data)
export const updateDataset = (id, data) => http.put(`/datasets/${id}`, data)
export const deleteDataset = (id) => http.delete(`/datasets/${id}`)

export const getMetrics = () => http.get('/metrics')
