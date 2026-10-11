package rulescript

import (
	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/norm"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
	"reflect"
)

func (r *Rule) Apply(f *norm.Factory, e memo.RelExpr) (result memo.RelExpr, ok bool) {
	if !r.From.mayMatch(e.Op()) {
		return nil, false
	}
	s := &state{
		f:               f,
		outputProjectID: rootOutputProjectID(r.To),
		toRootSetID:     r.To.ID,
		toRootValuesID:  r.To.ID,
		multiJoin:       r.From.joinCount() > 1 || r.To.joinCount() > 1,
		multiSet:        r.From.setCount() > 1 || r.To.setCount() > 1,
		rels:            make(map[string]memo.RelExpr),
		relOccurrences:  make(map[string][]memo.RelExpr),
		buildOccurrence: make(map[string]int),
		repeatedScans:   r.From.hasRepeatedScan(),
		toScanCounts:    make(map[string]int),
		empties:         make(map[string]memo.RelExpr),
		values:          make(map[string]memo.RelExpr),
		cols:            make(map[string][]opt.ColumnID),
		preds:           make(map[string]*predBinding),
		scalarPreds:     make(map[string]*projBinding),
		projs:           make(map[string]*projBinding),
		groups:          make(map[string]*projBinding),
		aggs:            make(map[string]*projBinding),
	}
	defer func() {
		if p := recover(); p != nil {
			if _, isAbort := p.(abort); isAbort {
				result, ok = nil, false
				return
			}
			panic(p)
		}
	}()
	if !s.matchRel(r.From, e) {
		return nil, false
	}
	s.rootCols = append([]opt.ColumnID{}, s.cols[r.From.ID]...)
	r.To.countScans(s.toScanCounts)
	out := s.buildRel(r.To)
	if (r.From.Op == "set" || r.To.Op == "set" || s.repeatedScans) &&
		!out.Relational().OutputCols.Equals(e.Relational().OutputCols) {
		out = s.renameOutput(out, s.cols[r.To.ID], s.cols[r.From.ID])
	}
	if !out.Relational().OutputCols.Equals(e.Relational().OutputCols) || out == e {
		return nil, false
	}
	return out, true
}

func (r *Rel) mayMatch(op opt.Operator) bool {
	switch r.Op {
	case "empty", "values":
		return op == opt.ValuesOp
	case "filter":
		return op == opt.SelectOp
	case "project":
		return op == opt.ProjectOp
	case "aggregate":
		if len(r.Groups) == 0 {
			return op == opt.ScalarGroupByOp
		}
		return op == opt.GroupByOp
	case "join":
		if r.Apply {
			switch r.Type {
			case "inner":
				return op == opt.InnerJoinApplyOp
			case "semi":
				return op == opt.SemiJoinApplyOp
			}
			return false
		}
		switch r.Type {
		case "inner":
			return op == opt.InnerJoinOp
		case "left":
			return op == opt.LeftJoinOp
		case "right":
			return op == opt.RightJoinOp
		case "full":
			return op == opt.FullJoinOp
		case "semi":
			return op == opt.SemiJoinOp
		case "anti":
			return op == opt.AntiJoinOp
		}
	case "set":
		return op == r.setOperator()
	}
	return true
}

type abort struct{}

type predBinding struct {
	filters  memo.FiltersExpr
	args     []opt.ColumnID
	operands []opt.ScalarExpr
}

type projBinding struct {
	elem     opt.ScalarExpr
	col      opt.ColumnID
	args     []opt.ColumnID
	argElems []opt.ScalarExpr
	distinct bool
}

type state struct {
	f               *norm.Factory
	rels            map[string]memo.RelExpr
	relOccurrences  map[string][]memo.RelExpr
	buildOccurrence map[string]int
	repeatedScans   bool
	toScanCounts    map[string]int
	empties         map[string]memo.RelExpr
	values          map[string]memo.RelExpr
	cols            map[string][]opt.ColumnID
	rootCols        []opt.ColumnID
	outputProjectID string
	toRootSetID     string
	toRootValuesID  string
	multiJoin       bool
	multiSet        bool
	preds           map[string]*predBinding
	scalarPreds     map[string]*projBinding
	projs           map[string]*projBinding
	groups          map[string]*projBinding
	aggs            map[string]*projBinding
	join            *memo.JoinPrivate
	set             *memo.SetPrivate
	exists          *memo.ExistsPrivate
}

func (r *Rel) countScans(counts map[string]int) {
	switch r.Op {
	case "scan":
		counts[r.Name]++
	case "filter", "project", "aggregate":
		r.Input.countScans(counts)
	case "join", "set":
		r.Left.countScans(counts)
		r.Right.countScans(counts)
	}
}

func (r *Rel) hasRepeatedScan() bool {
	counts := make(map[string]int)
	r.countScans(counts)
	for _, count := range counts {
		if count > 1 {
			return true
		}
	}
	return false
}

func (s *state) sameScan(a, b memo.RelExpr) bool {
	if a == b {
		return true
	}
	left, leftOK := a.(*memo.ScanExpr)
	right, rightOK := b.(*memo.ScanExpr)
	if !leftOK || !rightOK {
		return false
	}
	md := s.f.Metadata()
	if !md.Table(left.ScanPrivate.Table).Equals(md.Table(right.ScanPrivate.Table)) {
		return false
	}
	leftCols, rightCols := a.Relational().OutputCols.ToList(), b.Relational().OutputCols.ToList()
	if len(leftCols) != len(rightCols) {
		return false
	}
	for i := range leftCols {
		if left.ScanPrivate.Table.ColumnOrdinal(leftCols[i]) != right.ScanPrivate.Table.ColumnOrdinal(rightCols[i]) ||
			!md.ColumnMeta(leftCols[i]).Type.Equivalent(md.ColumnMeta(rightCols[i]).Type) {
			return false
		}
	}
	lp, rp := left.ScanPrivate, right.ScanPrivate
	lp.Table, rp.Table = 0, 0
	lp.Cols, rp.Cols = opt.ColSet{}, opt.ColSet{}
	return reflect.DeepEqual(lp, rp)
}

func (r *Rel) setCount() int {
	switch r.Op {
	case "filter", "project", "aggregate":
		return r.Input.setCount()
	case "join":
		return r.Left.setCount() + r.Right.setCount()
	case "set":
		return 1 + r.Left.setCount() + r.Right.setCount()
	default:
		return 0
	}
}

func (r *Rel) joinCount() int {
	switch r.Op {
	case "filter", "project", "aggregate":
		return r.Input.joinCount()
	case "join":
		return 1 + r.Left.joinCount() + r.Right.joinCount()
	case "set":
		return r.Left.joinCount() + r.Right.joinCount()
	default:
		return 0
	}
}

func (s *state) nullType(p *Rex) *types.T {
	switch p.Type {
	case "bool":
		return types.Bool
	case "int":
		return types.Int
	case "same_as":
		var typ *types.T
		for _, ref := range p.Refs {
			cols, ok := s.cols[ref.Of]
			if !ok || ref.Ordinal < 0 || ref.Ordinal >= len(cols) {
				panic(abort{})
			}
			colType := s.f.Metadata().ColumnMeta(cols[ref.Ordinal]).Type
			if typ != nil && !typ.Equivalent(colType) {
				panic(abort{})
			}
			typ = colType
		}
		if typ == nil {
			panic(abort{})
		}
		return typ
	}
	panic(abort{})
}

func rootOutputProjectID(r *Rel) string {
	for r.Op == "filter" {
		r = r.Input
	}
	if r.Op == "project" {
		return r.ID
	}
	return ""
}

func (r *Rel) setOperator() opt.Operator {
	switch r.Type {
	case "union":
		if r.All {
			return opt.UnionAllOp
		}
		return opt.UnionOp
	case "intersect":
		if r.All {
			return opt.IntersectAllOp
		}
		return opt.IntersectOp
	case "except":
		if r.All {
			return opt.ExceptAllOp
		}
		return opt.ExceptOp
	default:
		panic(abort{})
	}
}

func (s *state) reorderEmpty(side *Rel, cols []opt.ColumnID) {
	if side.Op != "empty" {
		return
	}
	if bound, ok := s.cols[side.ID]; ok && opt.MakeColSet(bound...).Equals(opt.MakeColSet(cols...)) {
		s.cols[side.ID] = append([]opt.ColumnID{}, cols...)
	}
}

func sameColumns(a, b []opt.ColumnID) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func (s *state) matchRel(p *Rel, e memo.RelExpr) bool {
	switch p.Op {
	case "scan":
		cols := e.Relational().OutputCols.ToList()
		if p.Arity > 0 && len(cols) != p.Arity {
			return false
		}
		if p.Unique && !e.Relational().FuncDeps.ColsAreStrictKey(e.Relational().OutputCols) {
			return false
		}
		for _, ordinal := range p.NonNull {
			if ordinal < 0 || ordinal >= len(cols) || !e.Relational().NotNullCols.Contains(cols[ordinal]) {
				return false
			}
		}
		if bound, ok := s.rels[p.Name]; ok {
			if !s.sameScan(bound, e) {
				return false
			}
			s.relOccurrences[p.Name] = append(s.relOccurrences[p.Name], e)
			s.cols[p.ID] = cols
			return true
		}
		s.rels[p.Name] = e
		s.relOccurrences[p.Name] = append(s.relOccurrences[p.Name], e)
		s.cols[p.ID] = cols
		return true

	case "empty":
		values, ok := e.(*memo.ValuesExpr)
		if !ok || len(values.Rows) != 0 {
			return false
		}
		cols := values.Private().(*memo.ValuesPrivate).Cols
		if len(cols) != p.Arity {
			return false
		}
		s.empties[p.ID] = e
		s.cols[p.ID] = append([]opt.ColumnID{}, cols...)
		return true

	case "values":
		values, ok := e.(*memo.ValuesExpr)
		if !ok || len(values.Rows) != len(p.Rows) {
			return false
		}
		cols := values.Private().(*memo.ValuesPrivate).Cols
		if len(cols) != len(p.Rows[0]) {
			return false
		}
		for i, row := range p.Rows {
			tuple, ok := values.Rows[i].(*memo.TupleExpr)
			if !ok || len(tuple.Elems) != len(row) {
				return false
			}
			for j, cell := range row {
				if !s.matchScalar(cell, tuple.Elems[j]) {
					return false
				}
			}
		}
		s.values[p.ID] = e
		s.cols[p.ID] = append([]opt.ColumnID{}, cols...)
		return true

	case "filter":
		sel, ok := e.(*memo.SelectExpr)
		if !ok || !s.matchRel(p.Input, sel.Input) {
			return false
		}
		s.cols[p.ID] = s.cols[p.Input.ID]
		return s.matchCond(p.Cond, sel.Filters)

	case "project":
		proj, ok := e.(*memo.ProjectExpr)
		if !ok {
			return false
		}
		if !s.matchRel(p.Input, proj.Input) {
			return false
		}
		out := make([]opt.ColumnID, len(p.Exprs))
		passthrough := opt.ColSet{}
		item := 0
		for i, expr := range p.Exprs {
			switch expr.Op {
			case "field":
				cols, matched := s.matchArgs([]*Rex{expr})
				if !matched || len(cols) != 1 || !proj.Passthrough.Contains(cols[0]) || passthrough.Contains(cols[0]) {
					return false
				}
				passthrough.Add(cols[0])
				out[i] = cols[0]
			case "field_alias":
				cols, matched := s.matchArgs([]*Rex{expr})
				if !matched || len(cols) != 1 || item >= len(proj.Projections) {
					return false
				}
				v, ok := proj.Projections[item].Element.(*memo.VariableExpr)
				if !ok || v.Col != cols[0] || proj.Projections[item].Col == v.Col {
					return false
				}
				out[i] = proj.Projections[item].Col
				item++
			case "proj", "scalar", "true", "false", "null", "int":
				if item >= len(proj.Projections) {
					return false
				}
				if expr.Op == "proj" {
					if !s.matchProjection(expr, &proj.Projections[item]) {
						return false
					}
				} else if !s.matchScalar(expr, proj.Projections[item].Element) {
					return false
				}
				out[i] = proj.Projections[item].Col
				item++
			default:
				return false
			}
		}
		if item != len(proj.Projections) || !passthrough.Equals(proj.Passthrough) {
			return false
		}
		s.cols[p.ID] = out
		return true

	case "aggregate":
		var source memo.RelExpr
		var aggregations memo.AggregationsExpr
		var private *memo.GroupingPrivate
		if len(p.Groups) == 0 {
			group, ok := e.(*memo.ScalarGroupByExpr)
			if !ok {
				return false
			}
			source, aggregations, private = group.Input, group.Aggregations, &group.GroupingPrivate
		} else {
			group, ok := e.(*memo.GroupByExpr)
			if !ok {
				return false
			}
			source, aggregations, private = group.Input, group.Aggregations, &group.GroupingPrivate
		}
		if !private.Ordering.Any() || private.NullsAreDistinct || private.ErrorOnDup != "" ||
			len(aggregations) != len(p.Aggs) {
			return false
		}
		var keyProject *memo.ProjectExpr
		var ok bool
		computedNames := make(map[string]bool)
		for _, key := range p.Groups {
			if key.Op == "group_key" {
				computedNames[key.Name] = true
			}
		}
		for _, agg := range p.Aggs {
			for _, arg := range agg.Args {
				if arg.Op == "group_key" {
					computedNames[arg.Name] = true
				}
			}
		}
		computed := len(computedNames)
		itemOf := make(map[string]int)
		usedItems := make(map[int]bool)
		if computed > 0 {
			keyProject, ok = source.(*memo.ProjectExpr)
			if !ok || len(keyProject.Projections) != computed {
				return false
			}
			source = keyProject.Input
		}
		if !s.matchRel(p.Input, source) {
			return false
		}
		if keyProject != nil && !keyProject.Passthrough.SubsetOf(source.Relational().OutputCols) {
			return false
		}
		out := make([]opt.ColumnID, 0, len(p.Groups)+len(p.Aggs))
		keys := opt.ColSet{}
		for _, key := range p.Groups {
			var col opt.ColumnID
			switch key.Op {
			case "field":
				args, matched := s.matchArgs([]*Rex{key})
				if !matched || len(args) != 1 {
					return false
				}
				col = args[0]
			case "group_key":
				if keyProject == nil {
					return false
				}
				index := -1
				for j := range keyProject.Projections {
					if usedItems[j] || !private.GroupingCols.Contains(keyProject.Projections[j].Col) {
						continue
					}
					if s.matchBoundScalar(key, keyProject.Projections[j].Element, keyProject.Projections[j].Col, s.groups) {
						index = j
						break
					}
				}
				if index < 0 {
					return false
				}
				usedItems[index] = true
				col = keyProject.Projections[index].Col
				itemOf[key.Name] = index
			default:
				return false
			}
			if keys.Contains(col) {
				return false
			}
			keys.Add(col)
			out = append(out, col)
		}
		if !keys.Equals(private.GroupingCols) {
			return false
		}
		if keyProject != nil && !keys.SubsetOf(keyProject.Relational().OutputCols) {
			return false
		}
		for i, agg := range p.Aggs {
			for _, arg := range agg.Args {
				if arg.Op == "group_key" {
					if keyProject == nil {
						return false
					}
					if index, seen := itemOf[arg.Name]; seen {
						if !s.matchBoundScalar(arg, keyProject.Projections[index].Element,
							keyProject.Projections[index].Col, s.groups) {
							return false
						}
						continue
					}
					used := usedColumns(aggregations[i].Agg)
					index := -1
					for j := range keyProject.Projections {
						if usedItems[j] || !used.Contains(keyProject.Projections[j].Col) {
							continue
						}
						if s.matchBoundScalar(arg, keyProject.Projections[j].Element, keyProject.Projections[j].Col, s.groups) {
							index = j
							break
						}
					}
					if index < 0 {
						return false
					}
					usedItems[index] = true
					itemOf[arg.Name] = index
				}
			}
			item := &aggregations[i]
			if keyProject != nil && !usedColumns(item.Agg).SubsetOf(keyProject.Relational().OutputCols) {
				return false
			}
			if !aggregateShape(item.Agg, agg.Distinct) ||
				!s.matchBoundScalar(agg, item.Agg, item.Col, s.aggs) {
				return false
			}
			s.aggs[agg.Name].distinct = agg.Distinct
			out = append(out, item.Col)
		}
		s.cols[p.ID] = out
		return true

	case "join":
		var left, right memo.RelExpr
		var on memo.FiltersExpr
		var private *memo.JoinPrivate
		switch join := e.(type) {
		case *memo.InnerJoinExpr:
			if !p.Apply && p.Type == "inner" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		case *memo.LeftJoinExpr:
			if !p.Apply && p.Type == "left" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		case *memo.RightJoinExpr:
			if !p.Apply && p.Type == "right" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		case *memo.FullJoinExpr:
			if !p.Apply && p.Type == "full" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		case *memo.SemiJoinExpr:
			if !p.Apply && p.Type == "semi" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		case *memo.AntiJoinExpr:
			if !p.Apply && p.Type == "anti" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		case *memo.InnerJoinApplyExpr:
			if p.Apply && p.Type == "inner" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		case *memo.SemiJoinApplyExpr:
			if p.Apply && p.Type == "semi" {
				left, right, on, private = join.Left, join.Right, join.On, &join.JoinPrivate
			}
		}
		if p.Apply && left != nil && right != nil &&
			right.Relational().OuterCols.Intersects(left.Relational().OutputCols) {
			return false
		}
		if private == nil || (s.multiJoin && !reflect.DeepEqual(*private, memo.JoinPrivate{})) ||
			!s.matchRel(p.Left, left) {
			return false
		}
		leftCols := append([]opt.ColumnID{}, s.cols[p.Left.ID]...)
		if !s.matchRel(p.Right, right) {
			return false
		}
		rightCols := append([]opt.ColumnID{}, s.cols[p.Right.ID]...)
		if left.Relational().OutputCols.Intersects(right.Relational().OutputCols) {
			return false
		}
		s.join = private
		cols := append(leftCols, rightCols...)
		s.cols[p.ID] = cols
		if !s.matchCond(p.Cond, on) {
			return false
		}
		if p.Type == "semi" || p.Type == "anti" {
			s.cols[p.ID] = leftCols
		}
		return true

	case "set":
		if e.Op() != p.setOperator() {
			return false
		}
		left, lok := e.Child(0).(memo.RelExpr)
		right, rok := e.Child(1).(memo.RelExpr)
		if !lok || !rok || !s.matchRel(p.Left, left) || !s.matchRel(p.Right, right) {
			return false
		}
		private := e.Private().(*memo.SetPrivate)
		s.reorderEmpty(p.Left, private.LeftCols)
		s.reorderEmpty(p.Right, private.RightCols)
		if !private.Ordering.Any() || !sameColumns(private.LeftCols, s.cols[p.Left.ID]) ||
			!sameColumns(private.RightCols, s.cols[p.Right.ID]) {
			return false
		}
		s.set = private
		s.cols[p.ID] = append([]opt.ColumnID{}, private.OutCols...)
		return true
	}
	return false
}

func (s *state) matchCond(p *Rex, filters memo.FiltersExpr) bool {
	switch p.Op {
	case "true":
		return len(filters) == 0
	case "false", "null", "scalar", "not", "or", "and_scalar", "in_single", "not_in_single", "exists":
		return len(filters) == 1 && s.matchScalar(p, filters[0].Condition)
	case "and":
		if len(filters) == 1 && filters[0].Condition.Op() == opt.AndOp {
			var leaves []opt.ScalarExpr
			if !flattenScalarAnd(filters[0].Condition, &leaves) || len(leaves) != len(p.Args) {
				return false
			}
			for i, arg := range p.Args {
				if !s.matchScalar(arg, leaves[i]) {
					return false
				}
			}
			return true
		}
		if len(p.Args) != len(filters) {
			return false
		}
		for i, arg := range p.Args {
			if arg.Op == "and" || arg.Op == "true" || !s.matchCond(arg, filters[i:i+1]) {
				return false
			}
		}
		return true
	case "pred":
		withProjection := false
		for _, arg := range p.Args {
			withProjection = withProjection || arg.Op == "proj"
		}
		if withProjection {
			if len(filters) != 1 || filters[0].Condition.ChildCount() != len(p.Args) {
				return false
			}
			root := filters[0].Condition
			operands := make([]opt.ScalarExpr, len(p.Args))
			for i, arg := range p.Args {
				child, ok := root.Child(i).(opt.ScalarExpr)
				if !ok {
					return false
				}
				if arg.Op == "proj" {
					if !s.matchBoundScalar(arg, child, 0, s.projs) {
						return false
					}
				} else {
					cols, ok := s.matchArgs([]*Rex{arg})
					v, isVar := child.(*memo.VariableExpr)
					if !ok || len(cols) != 1 || !isVar || v.Col != cols[0] {
						return false
					}
				}
				operands[i] = child
			}
			if _, dup := s.preds[p.Name]; dup {
				return false
			}
			s.preds[p.Name] = &predBinding{filters: filters, operands: operands}
			return true
		}
		args, ok := s.matchArgs(p.Args)
		if !ok {
			return false
		}
		if previous, dup := s.preds[p.Name]; dup {
			if !sameColumns(previous.args, args) || len(previous.filters) != len(filters) {
				return false
			}
			for i := range filters {
				if previous.filters[i].Condition != filters[i].Condition {
					return false
				}
			}
			return true
		}
		allowed := opt.MakeColSet(args...)
		for i := range filters {
			if !filters[i].ScalarProps().OuterCols.SubsetOf(allowed) {
				return false
			}
		}
		s.preds[p.Name] = &predBinding{filters: filters, args: args}
		return true
	}
	return false
}

func flattenScalarAnd(e opt.ScalarExpr, out *[]opt.ScalarExpr) bool {
	if e.Op() != opt.AndOp {
		*out = append(*out, e)
		return true
	}
	if e.ChildCount() != 2 {
		return false
	}
	left, lok := e.Child(0).(opt.ScalarExpr)
	right, rok := e.Child(1).(opt.ScalarExpr)
	return lok && rok && flattenScalarAnd(left, out) && flattenScalarAnd(right, out)
}

func flattenScalarOr(e opt.ScalarExpr, out *[]opt.ScalarExpr) bool {
	if e.Op() != opt.OrOp {
		*out = append(*out, e)
		return true
	}
	if e.ChildCount() != 2 {
		return false
	}
	left, lok := e.Child(0).(opt.ScalarExpr)
	right, rok := e.Child(1).(opt.ScalarExpr)
	return lok && rok && flattenScalarOr(left, out) && flattenScalarOr(right, out)
}

func unaryIs(name string) (operator, right opt.Operator, ok bool) {
	switch name {
	case "IS NULL":
		return opt.IsOp, opt.NullOp, true
	case "IS NOT NULL":
		return opt.IsNotOp, opt.NullOp, true
	case "IS NOT FALSE":
		return opt.IsNotOp, opt.FalseOp, true
	case "IS TRUE":
		return opt.IsOp, opt.TrueOp, true
	case "IS NOT TRUE":
		return opt.IsNotOp, opt.TrueOp, true
	}
	return 0, 0, false
}

func scalarOperator(name string) opt.Operator {
	switch name {
	case "=":
		return opt.EqOp
	case "<>":
		return opt.NeOp
	case "<":
		return opt.LtOp
	case "<=":
		return opt.LeOp
	case ">":
		return opt.GtOp
	case ">=":
		return opt.GeOp
	case "IS NOT DISTINCT FROM":
		return opt.IsOp
	case "-":
		return opt.UnaryMinusOp
	}
	panic(abort{})
}

func (s *state) matchScalar(p *Rex, e opt.ScalarExpr) bool {
	switch p.Op {
	case "exists":
		ex, ok := e.(*memo.ExistsExpr)
		if !ok || !s.matchRel(p.Input, ex.Input) {
			return false
		}
		if s.exists != nil && !reflect.DeepEqual(*s.exists, ex.ExistsPrivate) {
			return false
		}
		s.exists = &ex.ExistsPrivate
		return true
	case "field", "joinfield":
		cols, ok := s.matchArgs([]*Rex{p})
		v, isVar := e.(*memo.VariableExpr)
		return ok && len(cols) == 1 && isVar && v.Col == cols[0]
	case "true":
		return e.Op() == opt.TrueOp
	case "false":
		return e.Op() == opt.FalseOp
	case "int":
		if e.Op() != opt.ConstOp || e.DataType().Family() != types.IntFamily {
			return false
		}
		value, ok := memo.ExtractConstDatum(e).(*tree.DInt)
		return ok && int(*value) == p.Value
	case "null":
		want := s.nullType(p)
		if e.Op() == opt.NullOp {
			return e.DataType().Family() == types.UnknownFamily || e.DataType().Equivalent(want)
		}
		if e.Op() != opt.CastOp || e.ChildCount() == 0 || e.Child(0).Op() != opt.NullOp {
			return false
		}
		return e.DataType().Equivalent(want)
	case "proj":
		return s.matchBoundScalar(p, e, 0, s.projs)
	case "pred":
		return s.matchBoundScalar(p, e, 0, s.scalarPreds)
	case "not":
		if e.Op() != opt.NotOp || e.ChildCount() != 1 {
			return false
		}
		child, ok := e.Child(0).(opt.ScalarExpr)
		return ok && s.matchScalar(p.Arg, child)
	case "or":
		if e.Op() != opt.OrOp || len(p.Args) < 2 || e.ChildCount() != 2 {
			return false
		}
		if len(p.Args) > 2 {
			var leaves []opt.ScalarExpr
			if !flattenScalarOr(e, &leaves) || len(leaves) != len(p.Args) {
				return false
			}
			for i, arg := range p.Args {
				if !s.matchScalar(arg, leaves[i]) {
					return false
				}
			}
			return true
		}
		left, lok := e.Child(0).(opt.ScalarExpr)
		right, rok := e.Child(1).(opt.ScalarExpr)
		return lok && rok && s.matchScalar(p.Args[0], left) && s.matchScalar(p.Args[1], right)
	case "and_scalar":
		if e.Op() != opt.AndOp || len(p.Args) < 2 || e.ChildCount() != 2 {
			return false
		}
		if len(p.Args) > 2 {
			var leaves []opt.ScalarExpr
			if !flattenScalarAnd(e, &leaves) || len(leaves) != len(p.Args) {
				return false
			}
			for i, arg := range p.Args {
				if !s.matchScalar(arg, leaves[i]) {
					return false
				}
			}
			return true
		}
		left, lok := e.Child(0).(opt.ScalarExpr)
		right, rok := e.Child(1).(opt.ScalarExpr)
		return lok && rok && s.matchScalar(p.Args[0], left) && s.matchScalar(p.Args[1], right)
	case "in_single", "not_in_single":
		operator := opt.InOp
		if p.Op == "not_in_single" {
			operator = opt.NotInOp
		}
		if e.Op() != operator || len(p.Args) != 2 || e.ChildCount() != 2 {
			return false
		}
		tuple, ok := e.Child(1).(*memo.TupleExpr)
		if !ok || len(tuple.Elems) != 1 {
			return false
		}
		left, lok := e.Child(0).(opt.ScalarExpr)
		return lok && s.matchScalar(p.Args[0], left) && s.matchScalar(p.Args[1], tuple.Elems[0])
	case "scalar":
		if operator, right, ok := unaryIs(p.Operator); ok {
			if len(p.Args) != 1 || e.ChildCount() != 2 {
				return false
			}
			left, ok := e.Child(0).(opt.ScalarExpr)
			return e.Op() == operator && e.Child(1).Op() == right && ok && s.matchScalar(p.Args[0], left)
		}
		if e.Op() != scalarOperator(p.Operator) || e.ChildCount() != len(p.Args) {
			return false
		}
		for i, arg := range p.Args {
			child, ok := e.Child(i).(opt.ScalarExpr)
			if !ok || !s.matchScalar(arg, child) {
				return false
			}
		}
		return true
	}
	return false
}

func (s *state) boundProjArgs(args []*Rex) ([]opt.ScalarExpr, bool) {
	if len(args) == 0 {
		return nil, false
	}
	elems := make([]opt.ScalarExpr, len(args))
	for i, a := range args {
		if a.Op != "proj" {
			return nil, false
		}
		b, ok := s.projs[a.Name]
		if !ok || b.elem == nil {
			return nil, false
		}
		elems[i] = b.elem
	}
	return elems, true
}

func usedColumnsExcept(e opt.Expr, skip []opt.ScalarExpr) opt.ColSet {
	for _, s := range skip {
		if e == opt.Expr(s) {
			return opt.ColSet{}
		}
	}
	if v, ok := e.(*memo.VariableExpr); ok {
		return opt.MakeColSet(v.Col)
	}
	cols := opt.ColSet{}
	for i := 0; i < e.ChildCount(); i++ {
		cols.UnionWith(usedColumnsExcept(e.Child(i), skip))
	}
	return cols
}

func (s *state) replaceElems(e opt.ScalarExpr, from, to []opt.ScalarExpr) opt.ScalarExpr {
	if len(from) != len(to) {
		panic(abort{})
	}
	var replace norm.ReplaceFunc
	replace = func(x opt.Expr) opt.Expr {
		for i, f := range from {
			if x == opt.Expr(f) {
				return to[i]
			}
		}
		return s.f.CopyAndReplaceDefault(x, replace)
	}
	return replace(e).(opt.ScalarExpr)
}

func (s *state) matchProjection(p *Rex, item *memo.ProjectionsItem) bool {
	if p.Op != "proj" {
		return false
	}
	if elems, nested := s.boundProjArgs(p.Args); nested {
		if _, dup := s.projs[p.Name]; dup || !usedColumnsExcept(item.Element, elems).Empty() {
			return false
		}
		s.projs[p.Name] = &projBinding{elem: item.Element, col: item.Col, argElems: elems}
		return true
	}
	args, ok := s.matchArgs(p.Args)
	if !ok || !item.ScalarProps().OuterCols.SubsetOf(opt.MakeColSet(args...)) {
		return false
	}
	if previous, dup := s.projs[p.Name]; dup {
		if previous.col != 0 || previous.elem != item.Element || !sameColumns(previous.args, args) {
			return false
		}
		previous.col = item.Col
		return true
	}
	s.projs[p.Name] = &projBinding{elem: item.Element, col: item.Col, args: args}
	return true
}

func (s *state) matchBoundScalar(
	p *Rex, elem opt.ScalarExpr, col opt.ColumnID, bindings map[string]*projBinding,
) bool {
	args, ok := s.matchArgs(p.Args)
	if !ok || !usedColumns(elem).SubsetOf(opt.MakeColSet(args...)) {
		return false
	}
	if previous, dup := bindings[p.Name]; dup {
		return previous.elem == elem && previous.col == col && sameColumns(previous.args, args)
	}
	bindings[p.Name] = &projBinding{elem: elem, col: col, args: args}
	return true
}

func usedColumns(e opt.Expr) opt.ColSet {
	if v, ok := e.(*memo.VariableExpr); ok {
		return opt.MakeColSet(v.Col)
	}
	cols := opt.ColSet{}
	for i := 0; i < e.ChildCount(); i++ {
		cols.UnionWith(usedColumns(e.Child(i)))
	}
	return cols
}

func aggregateShape(e opt.ScalarExpr, distinct bool) bool {
	if filter, ok := e.(*memo.AggFilterExpr); ok {
		e = filter.Input
	}
	if wrapper, ok := e.(*memo.AggDistinctExpr); ok {
		if !distinct {
			return false
		}
		e = wrapper.Input
	} else if distinct {
		return false
	}
	return opt.IsAggregateOp(e)
}

func (s *state) changeAggDistinct(e opt.ScalarExpr, distinct bool) opt.ScalarExpr {
	var filter opt.ScalarExpr
	if wrapped, ok := e.(*memo.AggFilterExpr); ok {
		filter = wrapped.Filter
		e = wrapped.Input
	}
	if wrapped, ok := e.(*memo.AggDistinctExpr); ok {
		e = wrapped.Input
	}
	if distinct {
		e = s.f.ConstructAggDistinct(e)
	}
	if filter != nil {
		e = s.f.ConstructAggFilter(e, filter)
	}
	return e
}

func (s *state) matchArgs(args []*Rex) ([]opt.ColumnID, bool) {
	var out []opt.ColumnID
	for _, a := range args {
		switch a.Op {
		case "row":
			for _, id := range a.Rels {
				cols, ok := s.cols[id]
				if !ok {
					return nil, false
				}
				out = append(out, cols...)
			}
		case "field", "field_alias":
			cols, ok := s.cols[a.Of]
			if !ok || a.Ordinal < 0 || a.Ordinal >= len(cols) {
				return nil, false
			}
			out = append(out, cols[a.Ordinal])
		case "joinfield":
			left, lok := s.cols[a.Left]
			right, rok := s.cols[a.Right]
			if !lok || !rok || a.Ordinal < 0 || a.Ordinal >= len(left)+len(right) {
				return nil, false
			}
			if a.Ordinal < len(left) {
				out = append(out, left[a.Ordinal])
			} else {
				out = append(out, right[a.Ordinal-len(left)])
			}
		case "group_key":
			binding, ok := s.groups[a.Name]
			if !ok {
				return nil, false
			}
			out = append(out, binding.col)
		default:
			return nil, false
		}
	}
	return out, true
}

func (s *state) buildRel(p *Rel) memo.RelExpr {
	switch p.Op {
	case "scan":
		occurrences := s.relOccurrences[p.Name]
		if len(occurrences) == 0 {
			panic(abort{})
		}
		index := s.buildOccurrence[p.Name]
		if s.toScanCounts[p.Name] == 1 && len(occurrences) > 1 {
			for i, candidate := range occurrences {
				if sameColumns(candidate.Relational().OutputCols.ToList(), s.rootCols) {
					index = i
					break
				}
			}
		}
		if index >= len(occurrences) {
			if len(occurrences) != 1 {
				panic(abort{})
			}
			if s.repeatedScans {
				e := occurrences[0]
				cols := e.Relational().OutputCols.ToList()
				fresh := make([]opt.ColumnID, len(cols))
				for i, col := range cols {
					fresh[i] = s.f.Metadata().AddColumn("duplicate", s.f.Metadata().ColumnMeta(col).Type)
				}
				s.buildOccurrence[p.Name]++
				s.cols[p.ID] = fresh
				return s.renameOutput(e, cols, fresh)
			}
			index = 0
		}
		e := occurrences[index]
		s.buildOccurrence[p.Name]++
		s.cols[p.ID] = e.Relational().OutputCols.ToList()
		return e

	case "empty":
		if e, ok := s.empties[p.ID]; ok {
			s.cols[p.ID] = append([]opt.ColumnID{}, e.Private().(*memo.ValuesPrivate).Cols...)
			return e
		}
		if len(s.rootCols) != p.Arity {
			panic(abort{})
		}
		s.cols[p.ID] = append([]opt.ColumnID{}, s.rootCols...)
		return s.f.ConstructValues(nil, &memo.ValuesPrivate{
			Cols: append(opt.ColList{}, s.rootCols...),
			ID:   s.f.Metadata().NextUniqueID(),
		})

	case "values":
		if e, ok := s.values[p.ID]; ok {
			s.cols[p.ID] = append([]opt.ColumnID{}, e.Private().(*memo.ValuesPrivate).Cols...)
			return e
		}
		var cols []opt.ColumnID
		if p.ID == s.toRootValuesID && len(s.rootCols) == len(p.Rows[0]) {
			cols = append([]opt.ColumnID{}, s.rootCols...)
		} else if p.ReuseColsOf != "" {
			source, ok := s.cols[p.ReuseColsOf]
			if !ok || len(p.Ordinals) != len(p.Rows[0]) {
				panic(abort{})
			}
			for _, ordinal := range p.Ordinals {
				if ordinal < 0 || ordinal >= len(source) {
					panic(abort{})
				}
				cols = append(cols, source[ordinal])
			}
		} else {
			panic(abort{})
		}
		tupleTypes := make([]*types.T, len(cols))
		for i, col := range cols {
			tupleTypes[i] = s.f.Metadata().ColumnMeta(col).Type
		}
		rows := make(memo.ScalarListExpr, len(p.Rows))
		for i, row := range p.Rows {
			elems := make(memo.ScalarListExpr, len(row))
			for j, cell := range row {
				elems[j] = s.buildScalar(cell)
				if !elems[j].DataType().Equivalent(tupleTypes[j]) {
					panic(abort{})
				}
			}
			rows[i] = s.f.ConstructTuple(elems, types.MakeTuple(tupleTypes))
		}
		s.cols[p.ID] = cols
		return s.f.ConstructValues(rows, &memo.ValuesPrivate{
			Cols: append(opt.ColList{}, cols...), ID: s.f.Metadata().NextUniqueID(),
		})

	case "filter":
		input := s.buildRel(p.Input)
		filters := s.buildCond(p.Cond)
		s.cols[p.ID] = s.cols[p.Input.ID]
		if len(filters) == 0 {
			return input
		}
		return s.f.ConstructSelect(input, filters)

	case "project":
		input := s.buildRel(p.Input)
		if _, matched := s.cols[p.ID]; !matched && p.ID == s.outputProjectID && len(s.rootCols) != len(p.Exprs) {
			panic(abort{})
		}
		items := make(memo.ProjectionsExpr, 0, len(p.Exprs))
		passthrough := opt.ColSet{}
		out := make([]opt.ColumnID, len(p.Exprs))
		for i, e := range p.Exprs {
			switch e.Op {
			case "field":
				args := s.buildArgs([]*Rex{e})
				if len(args) != 1 {
					panic(abort{})
				}
				v, ok := args[0].(*memo.VariableExpr)
				if !ok || passthrough.Contains(v.Col) {
					panic(abort{})
				}
				passthrough.Add(v.Col)
				out[i] = v.Col
			case "field_alias":
				args := s.buildArgs([]*Rex{e})
				if len(args) != 1 {
					panic(abort{})
				}
				cols, ok := s.cols[p.ID]
				if !ok && p.ID == s.outputProjectID {
					cols = s.rootCols
				}
				if i >= len(cols) {
					panic(abort{})
				}
				item := s.f.ConstructProjectionsItem(args[0], cols[i])
				items = append(items, item)
				out[i] = item.Col
			case "proj":
				item := s.buildProjection(e)
				items = append(items, item)
				out[i] = item.Col
			case "scalar", "true", "false", "null", "int":
				cols, ok := s.cols[p.ID]
				if !ok && p.ID == s.outputProjectID {
					cols = s.rootCols
				}
				if i >= len(cols) {
					panic(abort{})
				}
				item := s.f.ConstructProjectionsItem(s.buildScalar(e), cols[i])
				items = append(items, item)
				out[i] = item.Col
			default:
				panic(abort{})
			}
		}
		s.cols[p.ID] = out
		return s.f.ConstructProject(input, items, passthrough)

	case "aggregate":
		input := s.buildRel(p.Input)
		out := make([]opt.ColumnID, 0, len(p.Groups)+len(p.Aggs))
		keys := opt.ColSet{}
		keyItems := memo.ProjectionsExpr{}
		for _, key := range p.Groups {
			var col opt.ColumnID
			switch key.Op {
			case "field":
				args := s.buildArgs([]*Rex{key})
				if len(args) != 1 {
					panic(abort{})
				}
				v, ok := args[0].(*memo.VariableExpr)
				if !ok {
					panic(abort{})
				}
				col = v.Col
			case "group_key":
				item := s.buildBoundScalar(key, s.groups)
				col = item.Col
				keyItems = append(keyItems, item)
			default:
				panic(abort{})
			}
			if keys.Contains(col) {
				panic(abort{})
			}
			keys.Add(col)
			out = append(out, col)
		}
		if len(keyItems) > 0 {
			input = s.f.ConstructProject(input, keyItems, input.Relational().OutputCols)
		}
		items := make(memo.AggregationsExpr, len(p.Aggs))
		for i, agg := range p.Aggs {
			binding, ok := s.aggs[agg.Name]
			if !ok {
				panic(abort{})
			}
			args := s.buildArgs(agg.Args)
			elem := binding.elem
			if binding.distinct != agg.Distinct {
				elem = s.changeAggDistinct(elem, agg.Distinct)
			}
			if !s.isIdentity(binding.args, args) {
				elem = s.substitute(elem, binding.args, args)
			}
			items[i] = s.f.ConstructAggregationsItem(elem, binding.col)
			out = append(out, binding.col)
		}
		s.cols[p.ID] = out
		if len(p.Groups) == 0 {
			return s.f.ConstructScalarGroupBy(input, items, &memo.GroupingPrivate{})
		}
		return s.f.ConstructGroupBy(input, items, &memo.GroupingPrivate{GroupingCols: keys})

	case "join":
		left := s.buildRel(p.Left)
		leftCols := append([]opt.ColumnID{}, s.cols[p.Left.ID]...)
		right := s.buildRel(p.Right)
		rightCols := append([]opt.ColumnID{}, s.cols[p.Right.ID]...)
		if left.Relational().OutputCols.Intersects(right.Relational().OutputCols) {
			panic(abort{})
		}
		s.cols[p.ID] = append(leftCols, rightCols...)
		on := s.buildCond(p.Cond)
		private := memo.JoinPrivate{}
		if s.join != nil && !s.multiJoin {
			private = *s.join
		}
		var out memo.RelExpr
		if p.Apply {
			switch p.Type {
			case "inner":
				out = s.f.ConstructInnerJoinApply(left, right, on, &private)
			case "semi":
				out = s.f.ConstructSemiJoinApply(left, right, on, &private)
			default:
				panic(abort{})
			}
		} else {
			switch p.Type {
			case "inner":
				out = s.f.ConstructInnerJoin(left, right, on, &private)
			case "left":
				out = s.f.ConstructLeftJoin(left, right, on, &private)
			case "right":
				out = s.f.ConstructRightJoin(left, right, on, &private)
			case "full":
				out = s.f.ConstructFullJoin(left, right, on, &private)
			case "semi":
				out = s.f.ConstructSemiJoin(left, right, on, &private)
			case "anti":
				out = s.f.ConstructAntiJoin(left, right, on, &private)
			default:
				panic(abort{})
			}
		}
		if p.Type == "semi" || p.Type == "anti" {
			s.cols[p.ID] = leftCols
		}
		return out

	case "set":
		left := s.buildRel(p.Left)
		right := s.buildRel(p.Right)
		leftCols := s.cols[p.Left.ID]
		rightCols := s.cols[p.Right.ID]
		if len(leftCols) != len(rightCols) {
			panic(abort{})
		}
		private := memo.SetPrivate{}
		if s.multiSet {
			if len(leftCols) != len(rightCols) {
				panic(abort{})
			}
			if p.ID == s.toRootSetID && len(s.rootCols) == len(leftCols) {
				private.OutCols = s.setOutCols(p.setOperator(), leftCols, left, right)
			} else {
				private.OutCols = make(opt.ColList, len(leftCols))
				for i, col := range leftCols {
					private.OutCols[i] = s.f.Metadata().AddColumn("rulescript_set", s.f.Metadata().ColumnMeta(col).Type)
				}
			}
			if left.Relational().OutputCols.Intersects(right.Relational().OutputCols) {
				fresh := make([]opt.ColumnID, len(rightCols))
				for i, col := range rightCols {
					fresh[i] = s.f.Metadata().AddColumn("rulescript_set_right", s.f.Metadata().ColumnMeta(col).Type)
				}
				right = s.renameOutput(right, rightCols, fresh)
				rightCols = fresh
			}
		} else if s.set == nil {
			if p.ID != s.toRootSetID || len(s.rootCols) != len(leftCols) {
				panic(abort{})
			}
			if left.Relational().OutputCols.Intersects(right.Relational().OutputCols) {
				fresh := make([]opt.ColumnID, len(rightCols))
				for i, col := range rightCols {
					fresh[i] = s.f.Metadata().AddColumn("rulescript_set_right", s.f.Metadata().ColumnMeta(col).Type)
				}
				right = s.renameOutput(right, rightCols, fresh)
				rightCols = fresh
			}
			private.OutCols = s.setOutCols(p.setOperator(), leftCols, left, right)
		} else {
			private = *s.set
			outCols := make(opt.ColList, len(leftCols))
			for i, col := range leftCols {
				matched := -1
				for j, old := range s.set.LeftCols {
					if old == col {
						matched = j
						break
					}
				}
				if matched < 0 || s.set.RightCols[matched] != rightCols[i] {
					panic(abort{})
				}
				outCols[i] = s.set.OutCols[matched]
			}
			private.OutCols = outCols
		}
		private.LeftCols = append(opt.ColList{}, leftCols...)
		private.RightCols = append(opt.ColList{}, rightCols...)
		s.cols[p.ID] = append([]opt.ColumnID{}, private.OutCols...)
		switch p.setOperator() {
		case opt.UnionOp:
			return s.f.ConstructUnion(left, right, &private)
		case opt.UnionAllOp:
			return s.f.ConstructUnionAll(left, right, &private)
		case opt.IntersectOp:
			return s.f.ConstructIntersect(left, right, &private)
		case opt.IntersectAllOp:
			return s.f.ConstructIntersectAll(left, right, &private)
		case opt.ExceptOp:
			return s.f.ConstructExcept(left, right, &private)
		case opt.ExceptAllOp:
			return s.f.ConstructExceptAll(left, right, &private)
		}
		panic(abort{})
	}
	panic(abort{})
}

func (s *state) setOutCols(op opt.Operator, leftCols []opt.ColumnID, left, right memo.RelExpr) opt.ColList {
	reused := false
	if op == opt.UnionOp || op == opt.UnionAllOp {
		for _, col := range s.rootCols {
			if left.Relational().OutputCols.Contains(col) || right.Relational().OutputCols.Contains(col) {
				reused = true
			}
		}
	}
	if !reused {
		return append(opt.ColList{}, s.rootCols...)
	}
	out := make(opt.ColList, len(leftCols))
	for i, col := range leftCols {
		out[i] = s.f.Metadata().AddColumn("rulescript_set", s.f.Metadata().ColumnMeta(col).Type)
	}
	return out
}

func (s *state) renameOutput(input memo.RelExpr, source, target []opt.ColumnID) memo.RelExpr {
	if len(source) != len(target) {
		panic(abort{})
	}
	items := make(memo.ProjectionsExpr, 0, len(source))
	pass := opt.ColSet{}
	seen := opt.ColSet{}
	for i, from := range source {
		to := target[i]
		if seen.Contains(to) || !input.Relational().OutputCols.Contains(from) {
			panic(abort{})
		}
		if !s.f.Metadata().ColumnMeta(from).Type.Equivalent(s.f.Metadata().ColumnMeta(to).Type) {
			panic(abort{})
		}
		seen.Add(to)
		if from == to {
			pass.Add(to)
		} else {
			items = append(items, s.f.ConstructProjectionsItem(s.f.ConstructVariable(from), to))
		}
	}
	return s.f.ConstructProject(input, items, pass)
}

func (s *state) buildCond(p *Rex) memo.FiltersExpr {
	switch p.Op {
	case "true":
		return nil
	case "false", "null", "scalar", "not", "or", "and_scalar", "in_single", "not_in_single", "exists":
		return memo.FiltersExpr{s.f.ConstructFiltersItem(s.buildScalar(p))}
	case "and":
		var out memo.FiltersExpr
		for _, a := range p.Args {
			out = append(out, s.buildCond(a)...)
		}
		return out
	case "pred":
		b, ok := s.preds[p.Name]
		if !ok {
			if _, scalar := s.scalarPreds[p.Name]; scalar {
				return memo.FiltersExpr{s.f.ConstructFiltersItem(s.buildScalar(p))}
			}
			panic(abort{})
		}
		args := s.buildArgs(p.Args)
		if len(b.operands) > 0 {
			if len(args) != len(b.operands) || len(b.filters) != 1 {
				panic(abort{})
			}
			var replace norm.ReplaceFunc
			replace = func(e opt.Expr) opt.Expr {
				for i, operand := range b.operands {
					if e == operand {
						return args[i]
					}
				}
				return s.f.CopyAndReplaceDefault(e, replace)
			}
			root := replace(b.filters[0].Condition).(opt.ScalarExpr)
			if !root.DataType().Equivalent(types.Bool) {
				panic(abort{})
			}
			return memo.FiltersExpr{s.f.ConstructFiltersItem(root)}
		}
		if s.isIdentity(b.args, args) {
			return b.filters
		}
		out := make(memo.FiltersExpr, len(b.filters))
		for i := range b.filters {
			out[i] = s.f.ConstructFiltersItem(s.substitute(b.filters[i].Condition, b.args, args))
		}
		return out
	}
	panic(abort{})
}

func (s *state) buildScalar(p *Rex) opt.ScalarExpr {
	switch p.Op {
	case "exists":
		if s.exists == nil {
			panic(abort{})
		}
		private := *s.exists
		return s.f.ConstructExists(s.buildRel(p.Input), &private)
	case "field", "joinfield":
		args := s.buildArgs([]*Rex{p})
		if len(args) != 1 {
			panic(abort{})
		}
		return args[0]
	case "true":
		return s.f.ConstructTrue()
	case "false":
		return s.f.ConstructFalse()
	case "int":
		return s.f.ConstructConst(tree.NewDInt(tree.DInt(p.Value)), types.Int)
	case "null":
		return s.f.ConstructNull(s.nullType(p))
	case "not":
		return s.f.ConstructNot(s.buildScalar(p.Arg))
	case "or":
		if len(p.Args) < 2 {
			panic(abort{})
		}
		out := s.f.ConstructOr(s.buildScalar(p.Args[0]), s.buildScalar(p.Args[1]))
		for _, arg := range p.Args[2:] {
			out = s.f.ConstructOr(out, s.buildScalar(arg))
		}
		return out
	case "and_scalar":
		if len(p.Args) < 2 {
			panic(abort{})
		}
		out := s.f.ConstructAnd(s.buildScalar(p.Args[0]), s.buildScalar(p.Args[1]))
		for _, arg := range p.Args[2:] {
			out = s.f.ConstructAnd(out, s.buildScalar(arg))
		}
		return out
	case "in_single", "not_in_single":
		if len(p.Args) != 2 {
			panic(abort{})
		}
		left, right := s.buildScalar(p.Args[0]), s.buildScalar(p.Args[1])
		tuple := s.f.ConstructTuple(memo.ScalarListExpr{right}, types.MakeTuple([]*types.T{right.DataType()}))
		if p.Op == "not_in_single" {
			return s.f.ConstructNotIn(left, tuple)
		}
		return s.f.ConstructIn(left, tuple)
	case "proj", "pred":
		bindings := s.projs
		if p.Op == "pred" {
			bindings = s.scalarPreds
		}
		b, ok := bindings[p.Name]
		if !ok {
			panic(abort{})
		}
		args := s.buildArgs(p.Args)
		if s.isIdentity(b.args, args) {
			return b.elem
		}
		return s.substitute(b.elem, b.args, args)
	case "scalar":
		if p.Operator == "-" {
			if len(p.Args) != 1 {
				panic(abort{})
			}
			return s.f.ConstructUnaryMinus(s.buildScalar(p.Args[0]))
		}
		if operator, right, ok := unaryIs(p.Operator); ok {
			if len(p.Args) != 1 {
				panic(abort{})
			}
			left := s.buildScalar(p.Args[0])
			var rhs opt.ScalarExpr
			switch right {
			case opt.NullOp:
				rhs = memo.NullSingleton
			case opt.TrueOp:
				rhs = s.f.ConstructTrue()
			default:
				rhs = s.f.ConstructFalse()
			}
			if operator == opt.IsOp {
				return s.f.ConstructIs(left, rhs)
			}
			return s.f.ConstructIsNot(left, rhs)
		}
		if len(p.Args) != 2 {
			panic(abort{})
		}
		left, right := s.buildScalar(p.Args[0]), s.buildScalar(p.Args[1])
		switch p.Operator {
		case "=":
			return s.f.ConstructEq(left, right)
		case "<>":
			return s.f.ConstructNe(left, right)
		case "<":
			return s.f.ConstructLt(left, right)
		case "<=":
			return s.f.ConstructLe(left, right)
		case ">":
			return s.f.ConstructGt(left, right)
		case ">=":
			return s.f.ConstructGe(left, right)
		case "IS NOT DISTINCT FROM":
			return s.f.ConstructIs(left, right)
		}
	}
	panic(abort{})
}

func (s *state) buildProjection(p *Rex) memo.ProjectionsItem {
	b, ok := s.projs[p.Name]
	if p.Op != "proj" || !ok {
		panic(abort{})
	}
	if b.col == 0 {
		b.col = s.f.Metadata().AddColumn("computed", b.elem.DataType())
	}
	args := s.buildArgs(p.Args)
	if b.argElems != nil {
		return s.f.ConstructProjectionsItem(s.replaceElems(b.elem, b.argElems, args), b.col)
	}
	if s.isIdentity(b.args, args) {
		return s.f.ConstructProjectionsItem(b.elem, b.col)
	}
	elem := s.substitute(b.elem, b.args, args)
	return s.f.ConstructProjectionsItem(elem, b.col)
}

func (s *state) buildBoundScalar(p *Rex, bindings map[string]*projBinding) memo.ProjectionsItem {
	b, ok := bindings[p.Name]
	if !ok {
		panic(abort{})
	}
	args := s.buildArgs(p.Args)
	if s.isIdentity(b.args, args) {
		return s.f.ConstructProjectionsItem(b.elem, b.col)
	}
	return s.f.ConstructProjectionsItem(s.substitute(b.elem, b.args, args), b.col)
}

func (s *state) buildArgs(args []*Rex) []opt.ScalarExpr {
	var out []opt.ScalarExpr
	for _, a := range args {
		switch a.Op {
		case "row":
			for _, id := range a.Rels {
				cols, ok := s.cols[id]
				if !ok {
					panic(abort{})
				}
				for _, c := range cols {
					out = append(out, s.f.ConstructVariable(c))
				}
			}
		case "field", "field_alias":
			cols, ok := s.cols[a.Of]
			if !ok || a.Ordinal < 0 || a.Ordinal >= len(cols) {
				panic(abort{})
			}
			out = append(out, s.f.ConstructVariable(cols[a.Ordinal]))
		case "joinfield":
			left, lok := s.cols[a.Left]
			right, rok := s.cols[a.Right]
			if !lok || !rok || a.Ordinal < 0 || a.Ordinal >= len(left)+len(right) {
				panic(abort{})
			}
			if a.Ordinal < len(left) {
				out = append(out, s.f.ConstructVariable(left[a.Ordinal]))
			} else {
				out = append(out, s.f.ConstructVariable(right[a.Ordinal-len(left)]))
			}
		case "proj":
			b, ok := s.projs[a.Name]
			if !ok {
				panic(abort{})
			}
			sub := s.buildArgs(a.Args)
			if s.isIdentity(b.args, sub) {
				out = append(out, b.elem)
			} else {
				out = append(out, s.substitute(b.elem, b.args, sub))
			}
		case "group_key":
			binding, ok := s.groups[a.Name]
			if !ok {
				panic(abort{})
			}
			out = append(out, s.f.ConstructVariable(binding.col))
		case "true":
			out = append(out, s.f.ConstructTrue())
		case "false":
			out = append(out, s.f.ConstructFalse())
		case "int":
			out = append(out, s.f.ConstructConst(tree.NewDInt(tree.DInt(a.Value)), types.Int))
		default:
			panic(abort{})
		}
	}
	return out
}

func (s *state) isIdentity(from []opt.ColumnID, to []opt.ScalarExpr) bool {
	if len(from) != len(to) {
		panic(abort{})
	}
	for i, c := range from {
		v, ok := to[i].(*memo.VariableExpr)
		if !ok || v.Col != c {
			return false
		}
	}
	return true
}

func (s *state) substitute(e opt.ScalarExpr, from []opt.ColumnID, to []opt.ScalarExpr) opt.ScalarExpr {
	var replace norm.ReplaceFunc
	replace = func(e opt.Expr) opt.Expr {
		if v, ok := e.(*memo.VariableExpr); ok {
			for i, c := range from {
				if v.Col == c {
					return to[i]
				}
			}
			return e
		}
		return s.f.CopyAndReplaceDefault(e, replace)
	}
	return replace(e).(opt.ScalarExpr)
}
