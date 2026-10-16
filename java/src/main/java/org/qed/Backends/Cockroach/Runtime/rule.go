package rulescript

import (
	"encoding/json"
	"fmt"

	"github.com/cockroachdb/errors"
)

type Rule struct {
	Name string `json:"name"`
	Mode string `json:"mode"`
	From *Rel   `json:"from"`
	To   *Rel   `json:"to"`
}

type Rel struct {
	Op          string   `json:"op"`
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Arity       int      `json:"arity"`
	Rows        [][]*Rex `json:"rows"`
	ReuseColsOf string   `json:"reuse_cols_of"`
	Ordinals    []int    `json:"ordinals"`
	Unique      bool     `json:"unique"`
	NonNull     []int    `json:"non_null"`
	Type        string   `json:"type"`
	All         bool     `json:"all"`
	Apply       bool     `json:"apply"`
	Cond        *Rex     `json:"cond"`
	Exprs       []*Rex   `json:"exprs"`
	Groups      []*Rex   `json:"groups"`
	Aggs        []*Rex   `json:"aggs"`
	Input       *Rel     `json:"input"`
	Left        *Rel     `json:"left"`
	Right       *Rel     `json:"right"`
}

type Rex struct {
	Op       string    `json:"op"`
	Name     string    `json:"name"`
	Operator string    `json:"operator"`
	Type     string    `json:"type"`
	Value    int       `json:"value"`
	Refs     []TypeRef `json:"refs"`
	Ordinal  int       `json:"ordinal"`
	Of       string    `json:"of"`
	Left     string    `json:"left"`
	Right    string    `json:"right"`
	Rels     []string  `json:"rels"`
	Args     []*Rex    `json:"args"`
	Arg      *Rex      `json:"arg"`
	Scalar   *Rex      `json:"scalar"`
	Cmp      string    `json:"cmp"`
	Input    *Rel      `json:"input"`
	Distinct bool      `json:"distinct"`
}

type TypeRef struct {
	Of      string `json:"of"`
	Ordinal int    `json:"ordinal"`
}

func Parse(data []byte) (*Rule, error) {
	var r Rule
	if err := json.Unmarshal(data, &r); err != nil {
		return nil, errors.Wrap(err, "parsing rule")
	}
	if r.From == nil || r.To == nil {
		return nil, errors.Newf("rule %q needs both from and to", r.Name)
	}
	if err := r.From.check(); err != nil {
		return nil, errors.Wrapf(err, "rule %q (from)", r.Name)
	}
	if err := r.To.check(); err != nil {
		return nil, errors.Wrapf(err, "rule %q (to)", r.Name)
	}
	return &r, nil
}

func (r *Rel) check() error {
	switch r.Op {
	case "scan", "empty":
		return nil
	case "values":
		if len(r.Rows) == 0 {
			return errors.New("values needs rows")
		}
		width := len(r.Rows[0])
		for _, row := range r.Rows {
			if len(row) != width {
				return errors.New("values row widths differ")
			}
			for _, cell := range row {
				if cell == nil || (cell.Op != "int" && cell.Op != "true" && cell.Op != "false") {
					return errors.New("unsupported values literal")
				}
			}
		}
		if r.ReuseColsOf != "" && len(r.Ordinals) != width {
			return errors.New("values column mapping width differs")
		}
		return nil
	case "filter":
		if err := r.Cond.check(); err != nil {
			return err
		}
		return r.Input.check()
	case "project":
		for _, e := range r.Exprs {
			if err := e.check(); err != nil {
				return err
			}
		}
		return r.Input.check()
	case "aggregate":
		for _, g := range r.Groups {
			if g.Op != "field" && g.Op != "group_key" {
				return fmt.Errorf("unsupported grouping key %q", g.Op)
			}
			if err := g.check(); err != nil {
				return err
			}
		}
		for _, a := range r.Aggs {
			if a.Op != "agg" {
				return fmt.Errorf("unsupported aggregation %q", a.Op)
			}
			if err := a.check(); err != nil {
				return err
			}
		}
		return r.Input.check()
	case "join":
		switch r.Type {
		case "inner", "left", "right", "full", "semi", "anti":
		default:
			return fmt.Errorf("unsupported join type %q", r.Type)
		}
		if r.Apply && r.Type != "inner" && r.Type != "semi" {
			return fmt.Errorf("unsupported apply join type %q", r.Type)
		}
		if err := r.Cond.check(); err != nil {
			return err
		}
		if err := r.Left.check(); err != nil {
			return err
		}
		return r.Right.check()
	case "set":
		switch r.Type {
		case "union", "intersect", "except":
		default:
			return fmt.Errorf("unsupported set type %q", r.Type)
		}
		if err := r.Left.check(); err != nil {
			return err
		}
		return r.Right.check()
	}
	return fmt.Errorf("unsupported relational operator %q", r.Op)
}

func (x *Rex) check() error {
	if x == nil {
		return errors.New("missing expression")
	}
	switch x.Op {
	case "true", "false", "field", "field_alias", "joinfield", "row", "int":
		return nil
	case "null":
		if x.Type == "same_as" && len(x.Refs) == 0 {
			return errors.New("same_as NULL needs type references")
		}
		if x.Type != "bool" && x.Type != "int" && x.Type != "same_as" {
			return fmt.Errorf("unsupported NULL literal type %q", x.Type)
		}
		return nil
	case "exists":
		if x.Input == nil {
			return errors.New("exists needs relational input")
		}
		return x.Input.check()
	case "any":
		if x.Input == nil {
			return errors.New("any needs relational input")
		}
		if x.Cmp != "=" {
			return fmt.Errorf("unsupported quantified comparison %q", x.Cmp)
		}
		if err := x.Scalar.check(); err != nil {
			return err
		}
		return x.Input.check()
	case "not":
		return x.Arg.check()
	case "or":
		if len(x.Args) < 2 {
			return fmt.Errorf("or needs at least two arguments, got %d", len(x.Args))
		}
		for _, a := range x.Args {
			if err := a.check(); err != nil {
				return err
			}
		}
		return nil
	case "and_scalar":
		if len(x.Args) < 2 {
			return fmt.Errorf("and_scalar needs at least two arguments, got %d", len(x.Args))
		}
		for _, a := range x.Args {
			if err := a.check(); err != nil {
				return err
			}
		}
		return nil
	case "in_single", "not_in_single":
		if len(x.Args) != 2 {
			return fmt.Errorf("%s needs two arguments, got %d", x.Op, len(x.Args))
		}
		for _, a := range x.Args {
			if err := a.check(); err != nil {
				return err
			}
		}
		return nil
	case "scalar":
		arity := 2
		switch x.Operator {
		case "=", "<>", "<", "<=", ">", ">=", "IS NOT DISTINCT FROM":
		case "IS NULL", "IS NOT NULL", "IS NOT FALSE", "IS TRUE", "IS NOT TRUE":
			arity = 1
		case "+":
		case "-":
			if len(x.Args) == 1 {
				arity = 1
			}
		default:
			return fmt.Errorf("unsupported scalar operator %q", x.Operator)
		}
		if len(x.Args) != arity {
			return fmt.Errorf("scalar %q needs %d arguments", x.Operator, arity)
		}
		for _, a := range x.Args {
			if err := a.check(); err != nil {
				return err
			}
		}
		return nil
	case "pred", "proj", "group_key", "agg", "and":
		for _, a := range x.Args {
			if err := a.check(); err != nil {
				return err
			}
		}
		return nil
	}
	return fmt.Errorf("unsupported expression %q", x.Op)
}

func (r *Rule) GrowsOwnPattern() bool {
	return r.From.Op != "scan" && r.To.size() > r.From.size() && r.To.contains(r.From)
}

func (r *Rel) size() int {
	if r == nil || r.Op == "scan" {
		return 0
	}
	return 1 + r.Input.size() + r.Left.size() + r.Right.size()
}

func (r *Rel) contains(pattern *Rel) bool {
	if r == nil {
		return false
	}
	if r.embeds(pattern) {
		return true
	}
	return r.Input.contains(pattern) || r.Left.contains(pattern) || r.Right.contains(pattern)
}

func (r *Rel) embeds(pattern *Rel) bool {
	if pattern == nil {
		return true
	}
	if pattern.Op == "scan" {
		return true
	}
	if r == nil || r.Op != pattern.Op || r.Type != pattern.Type || r.Apply != pattern.Apply || r.All != pattern.All {
		return false
	}
	switch r.Op {
	case "filter":
		return pattern.Cond.embeds(r.Cond) && r.Input.embeds(pattern.Input)
	case "project":
		if len(r.Exprs) != len(pattern.Exprs) {
			return false
		}
		for i := range pattern.Exprs {
			if !pattern.Exprs[i].embeds(r.Exprs[i]) {
				return false
			}
		}
		return r.Input.embeds(pattern.Input)
	case "aggregate":
		return len(r.Groups) == len(pattern.Groups) && len(r.Aggs) == len(pattern.Aggs) && r.Input.embeds(pattern.Input)
	case "join":
		return pattern.Cond.embeds(r.Cond) && r.Left.embeds(pattern.Left) && r.Right.embeds(pattern.Right)
	case "set":
		return r.Left.embeds(pattern.Left) && r.Right.embeds(pattern.Right)
	case "empty":
		return r.Arity == pattern.Arity
	}
	return true
}

func (x *Rex) embeds(target *Rex) bool {
	if x == nil {
		return true
	}
	switch x.Op {
	case "pred", "proj", "group_key", "agg", "field", "field_alias", "joinfield", "row":
		return true
	}
	if target == nil || target.Op != x.Op || target.Operator != x.Operator || len(target.Args) != len(x.Args) {
		return false
	}
	for i := range x.Args {
		if !x.Args[i].embeds(target.Args[i]) {
			return false
		}
	}
	if x.Arg != nil {
		return x.Arg.embeds(target.Arg)
	}
	return true
}
