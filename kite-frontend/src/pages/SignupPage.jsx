import { useState } from 'react'
import { Button, Card } from 'react-bootstrap'
import { Link, useNavigate } from 'react-router-dom'
import { signup } from '../api/client'
import ErrorAlert from '../components/ErrorAlert'
import FormField from '../components/FormField'

export default function SignupPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const navigate = useNavigate()

  const handleSubmit = (e) => {
    e.preventDefault()
    setError(null)
    signup(email, password)
      .then(() => navigate('/login'))
      .catch((err) => setError(err.message))
  }

  return (
    <Card>
      <Card.Body>
        <Card.Title className="mb-3">Sign up</Card.Title>
        <ErrorAlert message={error} />
        <form onSubmit={handleSubmit}>
          <FormField
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <FormField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Button type="submit" variant="primary" className="w-100">
            Sign up
          </Button>
        </form>
        <p className="mt-3 mb-0 text-center">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </Card.Body>
    </Card>
  )
}
