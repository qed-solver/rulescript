package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestJoinPushThroughJoin(t *testing.T) {
	rule := loadRule(t, "JoinPushThroughJoin")
	e := newEnv(t)
	a, ac := e.scan("u", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("u", 1)
	pred := func(name string, cols ...opt.ScalarExpr) opt.ScalarExpr {
		return e.f.ConstructFunction(memo.ScalarListExpr(cols), &memo.FunctionPrivate{
			Name: name, Typ: types.Bool,
			Properties: &tree.FunctionProperties{},
			Overload: &tree.Overload{
				Types: tree.ParamTypes{{Typ: types.Int}}, ReturnType: tree.FixedReturnType(types.Bool),
				Class: tree.NormalClass,
			},
		})
	}
	abOn := e.filters(pred("SA", e.v(ac[0])), pred("SB", e.v(bc[0])))
	ab := e.f.ConstructInnerJoin(a, b, abOn, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(ab, c, e.filters(
		pred("ST", e.v(ac[0]), e.v(bc[0])),
		pred("TC", e.v(cc[0])),
	), &memo.JoinPrivate{})
	acOn := e.filters(pred("SA", e.v(ac[0])), pred("TC", e.v(cc[0])))
	acJoin := e.f.ConstructInnerJoin(a, c, acOn, &memo.JoinPrivate{})
	acbOn := e.filters(
		pred("ST", e.v(ac[0]), e.v(bc[0])),
		pred("SB", e.v(bc[0])),
	)
	acb := e.f.ConstructInnerJoin(acJoin, b, acbOn, &memo.JoinPrivate{})
	want := e.f.ConstructProject(acb, memo.EmptyProjectionsExpr, acb.Relational().OutputCols)
	e.expectRewrite(rule, before, want)

	nearC, nearCC := e.scan("u", 2)
	nearMiss := e.f.ConstructInnerJoin(ab, nearC, e.filters(
		pred("ST", e.v(ac[0]), e.v(bc[0])),
		pred("TC", e.v(nearCC[0])),
	), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
