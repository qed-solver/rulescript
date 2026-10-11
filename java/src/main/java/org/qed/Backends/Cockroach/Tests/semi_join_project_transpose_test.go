package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSemiJoinProjectTranspose(t *testing.T) {
	rule := loadRule(t, "SemiJoinProjectTranspose")
	e := newEnv(t)
	s, xc := e.scan("u", 2)
	r, yc := e.scan("t", 2)
	p0 := e.f.Metadata().AddColumn("p0", types.Int)
	p1 := e.f.Metadata().AddColumn("p1", types.Int)
	project := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(xc[0]), e.v(xc[1])), p0),
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(xc[0]), e.v(xc[1])), p1),
	}, opt.ColSet{})

	e.expectNoMatch(rule, e.f.ConstructSemiJoin(s, r,
		e.filters(e.f.ConstructLt(e.v(xc[0]), e.v(yc[0]))), &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(project, r,
		e.filters(e.f.ConstructLt(e.v(p0), e.v(yc[0]))), &memo.JoinPrivate{}))

	e.expectRewrite(rule, e.f.ConstructSemiJoin(project, r,
		e.filters(
			e.f.ConstructLt(e.v(p0), e.v(yc[0])),
			e.f.ConstructLt(e.v(p1), e.v(yc[1])),
		), &memo.JoinPrivate{}),
		e.f.ConstructProject(e.f.ConstructSemiJoin(s, r,
			e.filters(
				e.f.ConstructLt(e.f.ConstructPlus(e.v(xc[0]), e.v(xc[1])), e.v(yc[0])),
				e.f.ConstructLt(e.f.ConstructMult(e.v(xc[0]), e.v(xc[1])), e.v(yc[1])),
			), &memo.JoinPrivate{}),
			memo.ProjectionsExpr{
				e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(xc[0]), e.v(xc[1])), p0),
				e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(xc[0]), e.v(xc[1])), p1),
			}, opt.ColSet{}))
}
