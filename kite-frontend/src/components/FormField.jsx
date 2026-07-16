import { Form } from 'react-bootstrap'

export default function FormField({ label, type, value, onChange, ...props }) {
  return (
    <Form.Group className="mb-3">
      <Form.Label>{label}</Form.Label>
      <Form.Control type={type} value={value} onChange={onChange} required {...props} />
    </Form.Group>
  )
}
