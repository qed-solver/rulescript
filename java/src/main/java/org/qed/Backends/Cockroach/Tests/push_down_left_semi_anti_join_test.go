package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushDownLeftSemiAntiJoin(t *testing.T) {
	rule := loadRule(t, "PushDownLeftSemiAntiJoin")
	e := newEnv(t)
	x, xc := e.scan("t", 2)
	y, yc := e.scan("u", 2)
	p0 := e.f.Metadata().AddColumn("p0", types.Int)
	p1 := e.f.Metadata().AddColumn("p1", types.Int)
	leftProject := e.f.ConstructProject(x, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(xc[0]), p0),
		e.f.ConstructProjectionsItem(e.v(xc[1]), p1),
	}, opt.ColSet{})
	def := &memo.UDFDefinition{Name: "c", Typ: types.Bool, CalledOnNullInput: true}
	cond := e.f.ConstructUDFCall(memo.ScalarListExpr{e.v(p0), e.v(p1), e.v(yc[0]), e.v(yc[1])}, &memo.UDFCallPrivate{Def: def})
	before := e.f.ConstructSemiJoin(leftProject, y, e.filters(cond), &memo.JoinPrivate{})
	rawJoin := e.f.ConstructSemiJoin(x, y, e.filters(e.f.ConstructUDFCall(memo.ScalarListExpr{
		e.v(xc[0]), e.v(xc[1]), e.v(yc[0]), e.v(yc[1]),
	}, &memo.UDFCallPrivate{Def: def})), &memo.JoinPrivate{})
	want := e.f.ConstructProject(rawJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(xc[0]), p0),
		e.f.ConstructProjectionsItem(e.v(xc[1]), p1),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSemiJoin(e.f.ConstructProject(x, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(xc[0]), p0),
	}, opt.ColSet{}), y, e.filters(cond), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
