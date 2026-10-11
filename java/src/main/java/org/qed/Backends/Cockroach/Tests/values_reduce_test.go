package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestValuesReduce(t *testing.T) {
	rule := loadRule(t, "ValuesReduce")
	e := newEnv(t)
	a := e.f.Metadata().AddColumn("a", types.Int)
	b := e.f.Metadata().AddColumn("b", types.Int)
	c := e.f.Metadata().AddColumn("c", types.Int)
	x := e.f.Metadata().AddColumn("x", types.Int)
	y := e.f.Metadata().AddColumn("y", types.Int)
	values := e.intValues([]opt.ColumnID{a, b, c}, []int{1, 2, 3}, []int{4, 5, 6})
	before := e.f.ConstructProject(values, nil, opt.MakeColSet(c, a))
	want := e.intValues([]opt.ColumnID{c, a}, []int{3, 1}, []int{6, 4})
	e.expectRewrite(rule, before, want)

	s, sc := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(sc[2]), x),
		e.f.ConstructProjectionsItem(e.v(sc[0]), y),
	}, opt.MakeColSet()))

	e.expectNoMatch(rule, e.f.ConstructProject(values, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(3), x),
		e.f.ConstructProjectionsItem(e.v(a), y),
	}, opt.MakeColSet()))

	e.expectNoMatch(rule, e.f.ConstructProject(values, nil, opt.MakeColSet(a)))
}
