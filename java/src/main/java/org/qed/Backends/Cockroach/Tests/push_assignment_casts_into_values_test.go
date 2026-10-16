package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushAssignmentCastsIntoValues(t *testing.T) {
	rule := loadRule(t, "PushAssignmentCastsIntoValues")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	md := e.f.Metadata()
	casted := md.AddColumn("casted", types.Float)
	flag := md.AddColumn("flag", types.Bool)
	castItem := e.f.ConstructProjectionsItem(e.f.ConstructCast(e.v(cols[0]), types.Float), casted)
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{
		castItem, e.f.ConstructProjectionsItem(e.f.ConstructTrue(), flag),
	}, opt.ColSet{})
	inner := e.f.ConstructProject(s, memo.ProjectionsExpr{castItem}, opt.ColSet{})
	want := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), flag),
	}, opt.MakeColSet(casted))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		castItem, e.f.ConstructProjectionsItem(e.f.ConstructFalse(), flag),
	}, opt.ColSet{}))
}
