package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRejectNullsProject(t *testing.T) {
	rule := loadRule(t, "RejectNullsProject")
	e := newEnv(t)
	base, cols := e.scan("u", 2)
	s := e.f.ConstructProject(base, nil, opt.MakeColSet(cols[1]))
	a := cols[1]
	z := e.f.Metadata().AddColumn("z", types.Int)
	project := func(input memo.RelExpr, scalar opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructProject(input, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(scalar, z),
		}, opt.MakeColSet(a))
	}
	minus := e.f.ConstructUnaryMinus(e.v(a))
	before := e.f.ConstructSelect(project(s, minus), e.filters(e.f.ConstructIsNot(e.v(z), memo.NullSingleton)))
	inner := e.f.ConstructSelect(s, e.filters(e.f.ConstructIsNot(e.v(a), memo.NullSingleton)))
	want := e.f.ConstructSelect(project(inner, minus), e.filters(e.f.ConstructIsNot(e.v(z), memo.NullSingleton)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(project(s, minus), e.filters(e.f.ConstructIs(e.v(z), memo.NullSingleton))))
	e.expectNoMatch(rule, e.f.ConstructSelect(project(s, e.v(a)), e.filters(e.f.ConstructIsNot(e.v(z), memo.NullSingleton))))
}

func TestSQLRejectNullsProject(t *testing.T) {
	expectSQLFires(t, "RejectNullsProject", "SELECT z, a FROM (SELECT -a AS z, a FROM (VALUES (NULL::INT), (1)) AS v(a)) WHERE z IS NOT NULL")
}
