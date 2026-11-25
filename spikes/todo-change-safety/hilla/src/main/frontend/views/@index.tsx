import { TodoService } from '../generated/endpoints';

export default function TodoForm() {
  return <form onSubmit={event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    void TodoService.save({ title: String(form.get('title')) });
  }}><label>Todo title <input name="title" required /></label><button>Save</button></form>;
}
