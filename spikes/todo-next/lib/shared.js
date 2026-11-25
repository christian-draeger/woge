export const filters = ["all", "active", "completed"];
export const maxTitleLength = 120;
export const addPath = "/woge-actions/add-todo";
export const validTitle = title => title.trim().length >= 1 && title.trim().length <= maxTitleLength;
